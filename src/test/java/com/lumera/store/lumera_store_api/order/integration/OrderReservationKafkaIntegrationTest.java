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

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.notification.kafka.enabled=false", "app.payment.kafka.enabled=false", "app.inventory.kafka.enabled=true",
        "app.inventory.order-kafka.enabled=true", "app.order.kafka.enabled=true",
        "spring.kafka.consumer.group-id=order-flow-products", "app.inventory.order-kafka.group-id=order-flow-inventory",
        "app.order.kafka.group-id=order-flow-orders"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderReservationKafkaIntegrationTest {
    static final List<String> TOPICS = List.of("flow.products", "flow.orders", "flow.results");
    static final List<String> GROUPS = List.of("order-flow-products", "order-flow-inventory", "order-flow-orders");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        var names = List.of("product-created", "order-created", "stock-reservation-result");
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
            for(String topic:List.of("lumera.payment.processed.v1", "lumera.payment.requested.v1", "lumera.stock.finalization.requested.v1", "lumera.stock.finalized.v1", "lumera.order.completed.v1")) {
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

    @BeforeEach void clean() {
        await(() -> List.of("inventory-product-created","inventory-order-created","order-stock-reservation-result")
                .stream().allMatch(id -> {
                    var c=listeners.getListenerContainer(id);
                    return c!=null && c.getAssignedPartitions()!=null && c.getAssignedPartitions().size()==3;
                }), "Consumers assigned");
        for(String table:List.of("stock_reservation_items","stock_reservations","order_payment_results","payments","order_items","orders","inventories","outbox_events","products"))
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

    @Test void shouldReserveAllItemsAndAdvanceOrderThroughBothOutboxes() {
        UUID first=product(10), second=product(5);
        UUID order=order(first,3,second,2);
        assertEquals("PENDING_STOCK",status(order));
        drive(order,"PENDING_PAYMENT");
        assertStock(first,10,3); assertStock(second,5,2);
        assertEquals("RESERVED", reservationStatus(order));
        assertEquals(2,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type IN ('OrderCreated','StockReservationResult') AND published_at IS NOT NULL",order));
    }
    @Test void shouldRejectWholeOrderWithoutPartialReservation() {
        UUID first=product(10), second=product(1);
        UUID order=order(first,3,second,2);
        drive(order,"CANCELLED");
        assertStock(first,10,0); assertStock(second,1,0);
        assertEquals("REJECTED",reservationStatus(order));
        assertTrue(jdbc.queryForObject("SELECT rejection_reason FROM stock_reservations WHERE order_id=?",String.class,order)
                .contains("Insufficient stock"));
    }
    @Test void shouldRejectMissingInventory() {
        UUID product=product(0);
        jdbc.update("DELETE FROM inventories WHERE product_id=?",product);
        UUID order=order(product,1);
        drive(order,"CANCELLED");
        assertEquals("REJECTED",reservationStatus(order));
        assertEquals(0,count("SELECT count(*) FROM inventories WHERE product_id=?",product));
    }
    @Test void shouldPreserveStockAndResultOnRepeatedMessages() throws Exception {
        UUID product=product(10), order=order(product,3);
        drive(order,"PENDING_PAYMENT");
        String created=payload(order,"OrderCreated"), result=payload(order,"StockReservationResult");
        send(TOPICS.get(1),order,created); send(TOPICS.get(1),order,created);
        send(TOPICS.get(2),order,result); send(TOPICS.get(2),order,result);
        committed();
        assertStock(product,10,3);
        assertEquals("PENDING_PAYMENT",status(order));
        assertEquals(1,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='StockReservationResult'",order));
    }
    @Test void shouldRollbackStockAndDecisionWhenResultOutboxFails() {
        UUID product=product(10), order=order(product,3);
        StockReservationEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(resultOutbox);
        doThrow(new DataAccessResourceFailureException("Outbox unavailable")).when(target).append(any());
        assertThrows(DataAccessResourceFailureException.class, () -> reserve.execute(new ReserveOrderStockCommand(order,
                List.of(new StockReservationItem(product,3)))));
        assertStock(product,10,0);
        assertEquals(0,count("SELECT count(*) FROM stock_reservations WHERE order_id=?",order));
        assertEquals(0,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='StockReservationResult'",order));
        assertEquals("PENDING_STOCK",status(order));
        reset(target);
        drive(order,"PENDING_PAYMENT");
        assertStock(product,10,3);
    }
    @Test void shouldRetryTransientOutboxFailureWithoutDuplicatingStock() {
        UUID product=product(10), order=order(product,3);
        StockReservationEventOutboxAdapter target=AopTestUtils.getUltimateTargetObject(resultOutbox);
        doThrow(new DataAccessResourceFailureException("Temporary outbox failure"))
                .doCallRealMethod().when(target).append(any());
        drive(order,"PENDING_PAYMENT");
        committed();
        assertStock(product,10,3);
        assertEquals(1,count("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='StockReservationResult'",order));
        verify(target,times(2)).append(any());
    }
    @Test void shouldRejectChangedItemsForAnAlreadyReservedOrder() throws Exception {
        UUID product=product(10), order=order(product,3);
        drive(order,"PENDING_PAYMENT");
        String conflicting=payload(order,"OrderCreated").replace("\"quantity\":3", "\"quantity\":4");
        assertNotEquals(payload(order,"OrderCreated"),conflicting);
        try(var dlt=inspector(TOPICS.get(1)+".DLT")) {
            send(TOPICS.get(1),order,conflicting);
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Conflicting reservation must reach DLT");
        }
        assertStock(product,10,3); assertEquals("PENDING_PAYMENT",status(order));
    }
    @Test void shouldNotOverwriteOrderWithContradictoryResult() throws Exception {
        UUID product=product(10), order=order(product,3);
        drive(order,"PENDING_PAYMENT");
        var message=new com.lumera.store.lumera_store_api.order.adapter.in.messaging.StockReservationResultMessage(
                UUID.randomUUID(),order,java.time.Instant.now(),false,"No stock");
        try(var dlt=inspector(TOPICS.get(2)+".DLT")) {
            send(TOPICS.get(2),order,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(message));
            await(() -> !dlt.poll(Duration.ofMillis(100)).isEmpty(),"Conflicting result must reach DLT");
        }
        assertStock(product,10,3); assertEquals("PENDING_PAYMENT",status(order));
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void shouldRouteMalformedMessagesToTheirOwnDltAndContinue(int index) throws Exception {
        String topic=TOPICS.get(index);
        try(var dlt=inspector(topic+".DLT")) {
            UUID key=UUID.randomUUID(); send(topic,key,"{}");
            await(() -> {
                var records=dlt.poll(Duration.ofMillis(100));
                if(records.isEmpty()) return false;
                var record=records.iterator().next();
                assertEquals(key.toString(),record.key()); assertEquals("{}",record.value()); return true;
            },"Malformed event routed to correct DLT");
        }
        UUID product=product(5), order=order(product,2);
        drive(order,"PENDING_PAYMENT"); assertStock(product,5,2);
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
