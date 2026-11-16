package com.lumera.store.lumera_store_api.order.integration;

import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.inventory.application.command.*;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.outbox.StockReservationEventOutboxAdapter;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.order.application.command.*;
import com.lumera.store.lumera_store_api.order.application.port.in.CreateOrderUsecase;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.messaging.OutboxEventPublisher;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.notification.kafka.enabled=false", "app.payment.kafka.enabled=true", "app.payment.kafka.group-id=checkout-payments", "app.inventory.kafka.enabled=true",
        "app.inventory.order-kafka.enabled=true", "app.order.kafka.enabled=true",
        "spring.kafka.consumer.group-id=checkout-products", "app.inventory.order-kafka.group-id=checkout-inventory",
        "app.order.kafka.group-id=checkout-orders"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutKafkaIntegrationTest {
    static final List<String> TOPICS = List.of("checkout.products", "checkout.orders", "checkout.results", "checkout.payment-results", "checkout.payment-requests", "checkout.finalization-requests", "checkout.finalized", "checkout.completed");
    static final List<String> GROUPS = List.of("checkout-products", "checkout-inventory", "checkout-orders", "checkout-orders", "checkout-payments", "checkout-inventory", "checkout-orders", "unused-notifications");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        var names = List.of("product-created", "order-created", "stock-reservation-result", "payment-processed", "payment-requested", "stock-finalization-requested", "stock-finalized", "order-completed");
        for (int i=0;i<names.size();i++) {
            String name=names.get(i), topic=TOPICS.get(i);
            r.add("app.kafka.topics."+name, () -> topic);
            r.add("app.kafka.topics."+name+"-dlt", () -> topic+".DLT");
        }
        r.add("spring.kafka.consumer.properties[allow.auto.create.topics]", () -> false);
    }
    @BeforeAll static void topics() throws Exception {
        try(var admin=admin()) {
            var topics = new ArrayList<NewTopic>();
            for(String topic:TOPICS) {
                topics.add(new NewTopic(topic,3,(short)1));
                topics.add(new NewTopic(topic+".DLT",3,(short)1));
            }
            admin.createTopics(topics).all().get(20,TimeUnit.SECONDS);
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateProductsUsecase createProduct;
    @Autowired CreateOrderUsecase createOrder;
    @Autowired AddStockUsecase add;
    @Autowired ReserveOrderStockUsecase reserve;
    @Autowired OutboxEventPublisher publisher;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @MockitoSpyBean StockReservationEventOutboxAdapter resultOutbox;
    @MockitoSpyBean com.lumera.store.lumera_store_api.payment.adapter.out.gateway.SimulatedPaymentGatewayAdapter gateway;
    @MockitoSpyBean com.lumera.store.lumera_store_api.payment.adapter.out.persistence.outbox.PaymentEventOutboxAdapter paymentOutbox;
    @MockitoSpyBean com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter orderOutbox;
    @Autowired com.lumera.store.lumera_store_api.payment.application.port.in.ProcessPaymentUsecase processPayment;
    @Autowired com.lumera.store.lumera_store_api.order.application.port.in.ApplyPaymentResultUsecase applyPayment;
    @Autowired ConfirmOrderStockUsecase confirmStock;
    @Autowired ReleaseOrderStockUsecase releaseStock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired tools.jackson.databind.json.JsonMapper mapper;

    @BeforeEach void clean() {
        doReturn(com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision.APPROVED).when(gateway).charge(any(),any(),any());
        await(() -> List.of("inventory-product-created","inventory-order-created","order-stock-reservation-result", "payment-requested", "order-payment-processed", "inventory-stock-finalization", "order-stock-finalized")
                .stream().allMatch(id -> {
                    var c=listeners.getListenerContainer(id);
                    return c!=null && c.getAssignedPartitions()!=null && c.getAssignedPartitions().size()==3;
                }), "Consumers assigned");
        for(String table:List.of("stock_reservation_items","stock_reservations","order_payment_results","payments","order_items","orders","inventories","outbox_events","products"))
            jdbc.update("DELETE FROM "+table);
    }
    @AfterEach void committed() {
        try(var admin=admin()) {
            for(int i=0;i<TOPICS.size()-1;i++) {
                final String group=GROUPS.get(i);
                try(var c=inspector(TOPICS.get(i))) {
                    var ends=c.endOffsets(partitions(TOPICS.get(i)));
                    await(() -> {
                        try {
                            var offsets=admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(3,TimeUnit.SECONDS);
                            return ends.entrySet().stream().allMatch(e -> e.getValue()==0 || offsets.containsKey(e.getKey())
                                    && offsets.get(e.getKey()).offset()>=e.getValue());
                        } catch(Exception e) { throw new IllegalStateException(e); }
                    }, "Consumer offsets committed");
                }
            }
        }
    }
    @AfterAll static void stop(@Autowired KafkaListenerEndpointRegistry registry) { registry.stop(); }

    @Test void shouldApprovePaymentWithdrawStockAndConfirmOrder() {
        UUID first=product(10), second=product(5), order=order(first,3,second,2);
        drive(order,"CONFIRMED"); publisher.publishPending(); committed();
        assertStock(first,7,0); assertStock(second,3,0);
        assertEquals("CONFIRMED",reservationStatus(order));
        assertEquals("APPROVED",paymentStatus(order));
        assertEquals(7,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND published_at IS NOT NULL",order));
        assertEquals(1,count("SELECT count(*) FROM order_payment_results WHERE order_id=? AND approved",order));
        verify(gateway,times(1)).charge(any(),eq(order),eq(new BigDecimal("50.00")));
    }
    @Test void shouldDeclinePaymentReleaseStockAndCancelOrder() {
        decline();
        UUID product=product(10), order=order(product,3);
        drive(order,"CANCELLED"); committed();
        assertStock(product,10,0);
        assertEquals("RELEASED",reservationStatus(order));assertEquals("DECLINED",paymentStatus(order));
        assertEquals(1,count("SELECT count(*) FROM order_payment_results WHERE order_id=? AND NOT approved",order));
    }
    @Test void shouldNotChargeWhenStockWasRejected() {
        UUID product=product(1), order=order(product,3);
        drive(order,"CANCELLED"); committed();assertStock(product,1,0);
        assertEquals("REJECTED",reservationStatus(order));
        assertEquals(0,count("SELECT count(*) FROM payments WHERE order_id=?",order));
        verify(gateway,never()).charge(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void shouldTolerateAllMessagesBeingReplayedAfterCompletion(boolean approved) throws Exception {
        if(!approved) decline();
        UUID product=product(10), order=order(product,3);
        drive(order,approved?"CONFIRMED":"CANCELLED");committed();
        var dltBefore=TOPICS.stream().map(t -> endCount(t+".DLT")).toList();
        var events=List.of("OrderCreated","StockReservationResult","PaymentProcessed","PaymentRequested","StockFinalizationRequested","StockFinalized");
        // Replay in reverse flow order, including late initial stock decisions.
        for(int i=events.size()-1;i>=0;i--) send(TOPICS.get(i+1),order,payload(order,events.get(i)));
        committed();
        assertStock(product,approved?7:10,0);
        assertEquals(approved?"CONFIRMED":"CANCELLED",status(order));
        assertEquals(7,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",order));
        assertEquals(1,count("SELECT count(*) FROM payments WHERE order_id=?",order));
        verify(gateway,times(1)).charge(any(),eq(order),any());
        for(int i=0;i<TOPICS.size();i++) assertEquals(dltBefore.get(i).longValue(),endCount(TOPICS.get(i)+".DLT"));
    }
    @Test void shouldRetryTransientPaymentFailureAndCompleteOnce() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Temporary simulator failure"))
                .doReturn(com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision.APPROVED)
                .when(gateway).charge(any(),any(),any());
        UUID product=product(10), order=order(product,3);
        drive(order,"CONFIRMED");committed();assertStock(product,7,0);
        verify(gateway,times(2)).charge(any(),eq(order),any());
        assertEquals(1,count("SELECT count(*) FROM payments WHERE order_id=?",order));
    }
    @Test void shouldRollbackPaymentWhenResultOutboxFails() {
        var id=UUID.randomUUID();
        com.lumera.store.lumera_store_api.payment.adapter.out.persistence.outbox.PaymentEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(paymentOutbox);
        doThrow(new DataAccessResourceFailureException("Result outbox unavailable")).when(target).append(any());
        var command=new com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand(id,BigDecimal.TEN);
        assertThrows(DataAccessResourceFailureException.class,() -> processPayment.execute(command));
        assertEquals(0,count("SELECT count(*) FROM payments WHERE order_id=?",id));
        assertEquals(0,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",id));
        reset(target);processPayment.execute(command);
        assertEquals(1,count("SELECT count(*) FROM payments WHERE order_id=?",id));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void shouldRollbackStockFinalizationWhenOutboxFails(boolean confirmed) {
        UUID product=product(10), order=order(product,3);
        reserve.execute(new ReserveOrderStockCommand(order,List.of(new StockReservationItem(product,3))));
        StockReservationEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(resultOutbox);
        doThrow(new DataAccessResourceFailureException("Finalization outbox unavailable")).when(target).appendFinalized(any());
        assertThrows(DataAccessResourceFailureException.class,() -> {if(confirmed) confirmStock.execute(order);else releaseStock.execute(order);});
        assertStock(product,10,3);assertEquals("RESERVED",reservationStatus(order));
        assertEquals(0,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='StockFinalized'",order));
        reset(target);
        if(confirmed) confirmStock.execute(order);else releaseStock.execute(order);
        assertStock(product,confirmed?7:10,0);
        assertEquals(1,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='StockFinalized'",order));
    }
    @Test void shouldRollbackOrderAndPaymentReceiptWhenCommandOutboxFails() {
        UUID product=product(10),order=order(product,3);
        jdbc.update("UPDATE orders SET status='PENDING_PAYMENT' WHERE id=?",order);
        com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(orderOutbox);
        doThrow(new DataAccessResourceFailureException("Command outbox unavailable")).when(target).appendStockFinalizationRequested(any());
        var command=new ApplyPaymentResultCommand(order,UUID.randomUUID(),new BigDecimal("30.00"),true);
        assertThrows(DataAccessResourceFailureException.class,() -> applyPayment.execute(command));
        assertEquals("PENDING_PAYMENT",status(order));
        assertEquals(0,count("SELECT count(*) FROM order_payment_results WHERE order_id=?",order));
        reset(target);applyPayment.execute(command);
        assertEquals("PENDING_STOCK_CONFIRMATION",status(order));
    }
    @ParameterizedTest @ValueSource(ints={3,4,5,6})
    void shouldRouteMalformedPayloadToEachDltAndContinue(int index) throws Exception {
        try(var dlt=inspector(TOPICS.get(index)+".DLT")) {
            UUID key=UUID.randomUUID();send(TOPICS.get(index),key,"{}");
            await(() -> {
                var records=dlt.poll(Duration.ofMillis(100));if(records.isEmpty()) return false;
                var record=records.iterator().next();assertEquals(key.toString(),record.key());assertEquals("{}",record.value());return true;
            },"Malformed record must reach its route DLT");
        }
        UUID product=product(5),order=order(product,2);drive(order,"CONFIRMED");assertStock(product,3,0);
    }
    @Test void shouldRejectConflictingPaymentIdentityWithoutChangingCompletedOrder() throws Exception {
        UUID product=product(10),order=order(product,3);drive(order,"CONFIRMED");committed();
        var original=mapper.readValue(payload(order,"PaymentProcessed"),com.lumera.store.lumera_store_api.order.adapter.in.messaging.PaymentProcessedMessage.class);
        var conflict=new com.lumera.store.lumera_store_api.order.adapter.in.messaging.PaymentProcessedMessage(UUID.randomUUID(),UUID.randomUUID(),order,original.amount(),java.time.Instant.now(),original.status());
        try(var dlt=inspector(TOPICS.get(3)+".DLT")) {
            send(TOPICS.get(3),order,mapper.writeValueAsString(conflict));
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Changed payment identity must reach DLT");
        }
        assertEquals("CONFIRMED",status(order));assertStock(product,7,0);
    }
    @Test void shouldSerializeSimultaneousPaymentsForSameOrder() throws Exception {
        UUID id=UUID.randomUUID();var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var command=new com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand(id,BigDecimal.TEN);
            var a=pool.submit(() -> {start.await();return processPayment.execute(command);});
            var b=pool.submit(() -> {start.await();return processPayment.execute(command);});
            start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS).getId(),b.get(10,TimeUnit.SECONDS).getId());
        }
        assertEquals(1,count("SELECT count(*) FROM payments WHERE order_id=?",id));
        assertEquals(1,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",id));
        verify(gateway,times(1)).charge(any(),eq(id),any());
    }
    @Test void shouldKeepOrderPendingAndSendToDltAfterThreeTechnicalFailures() throws Exception {
        doThrow(new DataAccessResourceFailureException("Simulator unavailable")).when(gateway).charge(any(),any(),any());
        UUID product=product(10), order=order(product,3);
        try(var dlt=inspector(TOPICS.get(4)+".DLT")) {
            drive(order,"PENDING_PAYMENT");
            publisher.publishPending();
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Payment request must reach DLT after retries");
        }
        committed();
        assertEquals("PENDING_PAYMENT",status(order));assertStock(product,10,3);
        assertEquals(0,count("SELECT count(*) FROM payments WHERE order_id=?",order));
        verify(gateway,times(3)).charge(any(),eq(order),any());
    }
    @Test void shouldEnforcePaymentDatabaseConstraints() {
        UUID id=UUID.randomUUID();processPayment.execute(new com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand(id,BigDecimal.TEN));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO payments(id,order_id,amount,created_at,status) VALUES (?, ?, 10, now(), 'APPROVED')",UUID.randomUUID(),id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE payments SET amount=0 WHERE order_id=?",id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE payments SET status='UNKNOWN' WHERE order_id=?",id));
    }
    @Test void shouldUpgradeLegacyOrdersAndPaymentsWithoutLosingData() {
        String schema="checkout_upgrade";
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).target("8").load().migrate();
        UUID order=UUID.randomUUID(),payment=UUID.randomUUID();
        jdbc.update("INSERT INTO checkout_upgrade.orders(id,created_at,status) VALUES (?,now(),'PENDING_PAYMENT')",order);
        jdbc.update("INSERT INTO checkout_upgrade.payments(id,order_id,amount,created_at,status) VALUES (?, ?, 20, now(), 'APPROVED')",payment,order);
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).load().migrate();
        assertEquals("PENDING_PAYMENT",jdbc.queryForObject("SELECT status FROM checkout_upgrade.orders WHERE id=?",String.class,order));
        assertEquals(payment,jdbc.queryForObject("SELECT id FROM checkout_upgrade.payments WHERE order_id=?",UUID.class,order));
        jdbc.update("UPDATE checkout_upgrade.orders SET status='PENDING_STOCK_CONFIRMATION' WHERE id=?",order);
        jdbc.update("INSERT INTO checkout_upgrade.order_payment_results(order_id,payment_id,amount,approved) VALUES (?, ?, 20, true)",order,payment);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM checkout_upgrade.order_payment_results",Integer.class));
    }

    private void decline() {
        doReturn(com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision.DECLINED).when(gateway).charge(any(),any(),any());
    }
    private String paymentStatus(UUID id) { return jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,id); }
    private long endCount(String topic) { try(var c=inspector(topic)) {return c.endOffsets(partitions(topic)).values().stream().mapToLong(Long::longValue).sum();} }

    private UUID product(int stock) {
        UUID id=createProduct.execute("Mouse",null,BigDecimal.TEN,Category.OTHER).getId();
        publisher.publishPending();
        await(() -> count("SELECT count(*) FROM inventories WHERE product_id=?",id)==1,"Inventory initialized from product event");
        if(stock>0) add.execute(new StockMovementCommand(id,stock));
        return id;
    }
    private UUID order(Object... values) {
        var items=new ArrayList<CreateOrderItemCommand>();
        for(int i=0;i<values.length;i+=2) items.add(new CreateOrderItemCommand((UUID)values[i],(Integer)values[i+1]));
        return createOrder.execute(new CreateOrderCommand(items)).getId();
    }
    private void drive(UUID order,String expected) {
        await(() -> { publisher.publishPending(); return expected.equals(status(order)); },"Order must reach "+expected);
    }
    private String status(UUID id) { return jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id); }
    private String reservationStatus(UUID id) { return jdbc.queryForObject("SELECT status FROM stock_reservations WHERE order_id=?",String.class,id); }
    private String payload(UUID id,String type) { return jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregate_id=? AND event_type=?",String.class,id,type); }
    private int count(String sql,UUID id) { return jdbc.queryForObject(sql,Integer.class,id); }
    private void assertStock(UUID id,int quantity,int reserved) {
        assertEquals(quantity,jdbc.queryForObject("SELECT quantity FROM inventories WHERE product_id=?",Integer.class,id));
        assertEquals(reserved,jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE product_id=?",Integer.class,id));
    }
    private void send(String topic,UUID id,String payload) throws Exception { kafka.send(topic,id.toString(),payload).get(15,TimeUnit.SECONDS); }
    private static AdminClient admin() { return AdminClient.create(Map.of("bootstrap.servers",KAFKA.getBootstrapServers())); }
    private List<TopicPartition> partitions(String topic) { return IntStream.range(0,3).mapToObj(i -> new TopicPartition(topic,i)).toList(); }
    private KafkaConsumer<String,String> inspector(String topic) {
        var c=new KafkaConsumer<String,String>(Map.of("bootstrap.servers",KAFKA.getBootstrapServers(),"group.id",UUID.randomUUID().toString(),
                "enable.auto.commit","false","key.deserializer",StringDeserializer.class.getName(),"value.deserializer",StringDeserializer.class.getName()));
        var partitions=partitions(topic); c.assign(partitions); c.seekToEnd(partitions); partitions.forEach(c::position); return c;
    }
    private void await(BooleanSupplier condition,String message) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<deadline) {
            if(condition.getAsBoolean()) return;
            try { Thread.sleep(30); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        fail(message);
    }
}
