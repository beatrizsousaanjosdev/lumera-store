package com.lumera.store.lumera_store_api.notification.integration;

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

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.notification.kafka.enabled=true", "app.notification.kafka.group-id=notification-final-orders", "app.payment.kafka.enabled=true", "app.payment.kafka.group-id=notification-payments", "app.inventory.kafka.enabled=true",
        "app.inventory.order-kafka.enabled=true", "app.order.kafka.enabled=true",
        "spring.kafka.consumer.group-id=notification-products", "app.inventory.order-kafka.group-id=notification-inventory",
        "app.order.kafka.group-id=notification-orders"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationKafkaIntegrationTest {
    static final List<String> TOPICS = List.of("notification.products", "notification.orders", "notification.results", "notification.payment-results", "notification.payment-requests", "notification.finalization-requests", "notification.finalized", "notification.completed");
    static final List<String> GROUPS = List.of("notification-products", "notification-inventory", "notification-orders", "notification-orders", "notification-payments", "notification-inventory", "notification-orders", "notification-final-orders");
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
    @MockitoSpyBean com.lumera.store.lumera_store_api.payment.adapter.out.gateway.SimulatedPaymentGatewayAdapter gateway;
    @MockitoSpyBean com.lumera.store.lumera_store_api.notification.adapter.out.delivery.LoggingNotificationDeliveryAdapter delivery;
    @MockitoSpyBean com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter orderOutbox;
    @Autowired com.lumera.store.lumera_store_api.notification.application.port.in.NotifyOrderOutcomeUsecase notify;
    @Autowired com.lumera.store.lumera_store_api.notification.application.port.out.NotificationRepositoryPort notificationRepository;
    @Autowired com.lumera.store.lumera_store_api.order.application.port.in.CompleteOrderStockUsecase complete;
    @Autowired com.lumera.store.lumera_store_api.order.application.port.in.ApplyStockReservationResultUsecase applyStock;
    @Autowired tools.jackson.databind.json.JsonMapper mapper;

    @BeforeEach void clean() {
        doReturn(com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision.APPROVED).when(gateway).charge(any(),any(),any());
        await(() -> List.of("inventory-product-created","inventory-order-created","order-stock-reservation-result", "payment-requested", "order-payment-processed", "inventory-stock-finalization", "order-stock-finalized", "notification-order-completed")
                .stream().allMatch(id -> {
                    var c=listeners.getListenerContainer(id);
                    return c!=null && c.getAssignedPartitions()!=null && c.getAssignedPartitions().size()==3;
                }), "Consumers assigned");
        for(String table:List.of("order_notifications","stock_reservation_items","stock_reservations","order_payment_results","payments","order_items","orders","inventories","outbox_events","products"))
            jdbc.update("DELETE FROM "+table);
    }
    @AfterEach void committed() {
        try(var admin=admin()) {
            for(int i=0;i<TOPICS.size();i++) {
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

    @ParameterizedTest @ValueSource(booleans={true,false})
    void shouldNotifyCompletedCheckoutAfterApprovalOrRefusal(boolean approved) {
        if(!approved) doReturn(com.lumera.store.lumera_store_api.payment.application.port.out.PaymentDecision.DECLINED).when(gateway).charge(any(),any(),any());
        UUID product=product(10), order=order(product,3);
        finish(order,approved?"CONFIRMED":"CANCELLED");
        assertStock(product,approved?7:10,0);
        assertEquals(approved?"CONFIRMED":"CANCELLED",notificationOutcome(order));
        assertEquals(1,count("SELECT count(*) FROM order_notifications WHERE order_id=?",order));
        verify(deliveryTarget(),times(1)).deliver(argThat(n -> n.orderId().equals(order)));
    }
    @Test void shouldNotifyCancellationWhenInitialReservationIsRejected() {
        UUID product=product(1),order=order(product,3);
        finish(order,"CANCELLED");assertStock(product,1,0);
        assertEquals("CANCELLED",notificationOutcome(order));
        assertEquals(0,count("SELECT count(*) FROM payments WHERE order_id=?",order));
    }
    @Test void shouldDeduplicateRepeatedPayloadAndDifferentEventForSameOutcome() throws Exception {
        UUID product=product(10),order=order(product,3);finish(order,"CONFIRMED");
        String original=payload(order,"OrderCompleted");
        var message=mapper.readValue(original,com.lumera.store.lumera_store_api.notification.adapter.in.messaging.OrderCompletedMessage.class);
        var other=new com.lumera.store.lumera_store_api.notification.adapter.in.messaging.OrderCompletedMessage(UUID.randomUUID(),order,java.time.Instant.now(),message.status());
        send(TOPICS.get(7),order,original);send(TOPICS.get(7),order,mapper.writeValueAsString(other));committed();
        assertEquals(1,count("SELECT count(*) FROM order_notifications WHERE order_id=?",order));
        verify(deliveryTarget(),times(1)).deliver(any());assertEquals("CONFIRMED",status(order));
    }
    @Test void shouldRecoverTransientDeliveryFailureWithoutLosingCompletedOrder() {
        doThrow(new DataAccessResourceFailureException("Temporary delivery failure")).doCallRealMethod().when(deliveryTarget()).deliver(any());
        UUID product=product(10),order=order(product,3);finish(order,"CONFIRMED");
        assertEquals(1,count("SELECT count(*) FROM order_notifications WHERE order_id=?",order));
        verify(deliveryTarget(),times(2)).deliver(any());assertStock(product,7,0);
    }
    @Test void shouldKeepOrderConfirmedWhenNotificationFailsAndReprocessFromDlt() throws Exception {
        doThrow(new DataAccessResourceFailureException("Delivery unavailable")).when(deliveryTarget()).deliver(any());
        UUID product=product(10),order=order(product,3);
        try(var dlt=inspector(TOPICS.get(7)+".DLT")) {
            drive(order,"CONFIRMED");publisher.publishPending();
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Failed notification reaches DLT");
        }
        committed();assertEquals("CONFIRMED",status(order));assertStock(product,7,0);
        assertEquals(0,count("SELECT count(*) FROM order_notifications WHERE order_id=?",order));
        verify(deliveryTarget(),times(3)).deliver(any());
        reset(deliveryTarget());send(TOPICS.get(7),order,payload(order,"OrderCompleted"));
        await(() -> count("SELECT count(*) FROM order_notifications WHERE order_id=?",order)==1,"Manual replay recovers delivery");
        committed();verify(deliveryTarget(),times(1)).deliver(any());
    }
    @Test void shouldSendContradictoryOutcomeToDltAndPreserveOriginalNotification() throws Exception {
        UUID product=product(10),order=order(product,3);finish(order,"CONFIRMED");
        var conflict=new com.lumera.store.lumera_store_api.notification.adapter.in.messaging.OrderCompletedMessage(UUID.randomUUID(),order,java.time.Instant.now(),com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome.CANCELLED);
        try(var dlt=inspector(TOPICS.get(7)+".DLT")) {
            send(TOPICS.get(7),order,mapper.writeValueAsString(conflict));
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Conflict reaches DLT");
        }
        assertEquals("CONFIRMED",notificationOutcome(order));verify(deliveryTarget(),times(1)).deliver(any());
    }
    @ParameterizedTest @ValueSource(strings={"{","{}","null"})
    void shouldSendMalformedPayloadToDltAndContinue(String invalid) throws Exception {
        try(var dlt=inspector(TOPICS.get(7)+".DLT")) {
            UUID key=UUID.randomUUID();send(TOPICS.get(7),key,invalid);
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Invalid payload reaches DLT");
        }
        UUID product=product(5),order=order(product,2);finish(order,"CONFIRMED");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void shouldRollbackOrderCompletionWhenOutboxWriteFails(boolean confirmed) {
        UUID product=product(5),order=order(product,2);
        jdbc.update("UPDATE orders SET status=? WHERE id=?",confirmed?"PENDING_STOCK_CONFIRMATION":"PENDING_STOCK_RELEASE",order);
        com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(orderOutbox);
        doThrow(new DataAccessResourceFailureException("Outbox unavailable")).when(target).appendCompleted(any());
        assertThrows(DataAccessResourceFailureException.class,() -> complete.execute(new CompleteOrderStockCommand(order,confirmed)));
        assertEquals(confirmed?"PENDING_STOCK_CONFIRMATION":"PENDING_STOCK_RELEASE",status(order));
        assertEquals(0,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCompleted'",order));
        reset(target);complete.execute(new CompleteOrderStockCommand(order,confirmed));
        assertEquals(1,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCompleted'",order));
    }
    @Test void shouldRollbackInitialStockCancellationWhenCompletionOutboxFails() {
        UUID product=product(5),order=order(product,2);
        com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(orderOutbox);
        doThrow(new DataAccessResourceFailureException("Outbox unavailable")).when(target).appendCompleted(any());
        assertThrows(DataAccessResourceFailureException.class,() -> applyStock.execute(new StockReservationResultCommand(order,false,"No stock")));
        assertEquals("PENDING_STOCK",status(order));
        assertEquals(0,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCompleted'",order));
    }
    @Test void shouldSerializeConcurrentNotificationsForSameOrder() throws Exception {
        UUID order=UUID.randomUUID();var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> {start.await();return notify.execute(command(order));});
            var b=pool.submit(() -> {start.await();return notify.execute(command(order));});
            start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS).id(),b.get(10,TimeUnit.SECONDS).id());
        }
        assertEquals(1,count("SELECT count(*) FROM order_notifications WHERE order_id=?",order));
        verify(deliveryTarget(),times(1)).deliver(any());
    }
    @Test void shouldRequireTransactionForNotificationPersistence() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,() -> notificationRepository.lockByOrderId(UUID.randomUUID()));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,() -> notificationRepository.findByOrderId(UUID.randomUUID()));
    }
    @Test void shouldPreserveExistingOrdersWhenApplyingV11() {
        var old=org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas("notification_upgrade").target("10").load();
        old.migrate();UUID order=UUID.randomUUID();
        jdbc.update("INSERT INTO notification_upgrade.orders(id,created_at,status) VALUES (?,now(),'CONFIRMED')",order);
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas("notification_upgrade").load().migrate();
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM notification_upgrade.orders WHERE id=?",String.class,order));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_upgrade.order_notifications",Integer.class));
    }
    private com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand command(UUID order) {
        return new com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand(UUID.randomUUID(),order,com.lumera.store.lumera_store_api.notification.domain.NotificationOutcome.CONFIRMED);
    }
    private com.lumera.store.lumera_store_api.notification.adapter.out.delivery.LoggingNotificationDeliveryAdapter deliveryTarget() {return AopTestUtils.getUltimateTargetObject(delivery);}
    private String notificationOutcome(UUID order) {return jdbc.queryForObject("SELECT outcome FROM order_notifications WHERE order_id=?",String.class,order);}
    private void finish(UUID order,String expected) {
        drive(order,expected);
        await(() -> {publisher.publishPending();return count("SELECT count(*) FROM order_notifications WHERE order_id=?",order)==1;},"Order notification persisted");
        committed();assertEquals(expected,status(order));
    }

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
