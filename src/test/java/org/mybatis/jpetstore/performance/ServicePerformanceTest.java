/*
 *    Copyright 2010-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.jpetstore.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mybatis.jpetstore.performance.JetmSupport.MEASURED_ITERATIONS;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.jpetstore.domain.Account;
import org.mybatis.jpetstore.domain.Cart;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.mapper.MapperTestContext;
import org.mybatis.jpetstore.service.AccountService;
import org.mybatis.jpetstore.service.CatalogService;
import org.mybatis.jpetstore.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import etm.core.aggregation.Aggregate;
import etm.core.aggregation.RootAggregator;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.NestedMonitor;
import etm.core.timer.Java15NanoTimer;

/**
 * Tests de rendimiento de los servicios con JETM, sobre la base de datos HSQLDB en memoria.
 * <p>
 * Cada test mide un escenario con un punto de medición propio. Además, JETM está enganchado con Spring AOP a todos los
 * servicios y mappers (ver {@link Context}), así que dentro de cada escenario se mide también, de forma anidada, cada
 * llamada al servicio y cada consulta a la base de datos que hace por dentro.
 */
@Tag("performance")
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServicePerformanceTest.Context.class)
class ServicePerformanceTest {

  /** Tiempo medio máximo (ms) de una lectura: más de 10 veces lo medido (la más lenta, getOrder, tarda ~0,4 ms). */
  private static final double MAX_AVERAGE_READ_MS = 5;

  /** Tiempo medio máximo (ms) de una escritura: más de 10 veces lo medido (insertOrder tarda ~0,7 ms). */
  private static final double MAX_AVERAGE_WRITE_MS = 10;

  /** Rondas de calentamiento global, con todas las operaciones, antes de medir nada. */
  private static final int GLOBAL_WARMUP_ROUNDS = 1000;

  /** Artículos del carrito con el que se crean los pedidos. */
  private static final String[] ORDER_ITEM_IDS = { "EST-1", "EST-2", "EST-3" };

  /** Usuario con un número fijo de pedidos, para medir su consulta. */
  private static final String ORDERS_USERNAME = "perf-orders";

  /** Pedidos que se crean para ese usuario. */
  private static final int ORDERS_PER_USERNAME = 10;

  /** El monitor de JETM. */
  @Autowired
  private EtmMonitor monitor;

  /** The catalog service. */
  @Autowired
  private CatalogService catalogService;

  /** The account service. */
  @Autowired
  private AccountService accountService;

  /** The order service. */
  @Autowired
  private OrderService orderService;

  /** Cuenta con la que se crean los pedidos. */
  private Account account;

  /** Carrito con el que se crean los pedidos. */
  private Cart cart;

  /** Pedido ya guardado, para medir su lectura. */
  private int existingOrderId;

  /** Cuenta ya guardada, para medir su modificación. */
  private Account accountToUpdate;

  /** Contador para que cada cuenta nueva tenga un usuario distinto. */
  private int newAccountCount;

  /**
   * Prepara los datos de los escenarios y calienta todas las operaciones, sin medir nada.
   */
  @BeforeAll
  void prepareDataAndWarmUp() {
    monitor.disableCollection();
    try {
      account = accountService.getAccount("j2ee");
      cart = new Cart();
      for (String itemId : ORDER_ITEM_IDS) {
        cart.addItem(catalogService.getItem(itemId), true);
      }
      Order order = newOrder();
      orderService.insertOrder(order);
      existingOrderId = order.getOrderId();

      accountToUpdate = newAccount();
      accountService.insertAccount(accountToUpdate);

      for (int i = 0; i < ORDERS_PER_USERNAME; i++) {
        Order orderOfUser = newOrder();
        orderOfUser.setUsername(ORDERS_USERNAME);
        orderService.insertOrder(orderOfUser);
      }

      // El código que comparten todas las operaciones (MyBatis, JDBC, HSQLDB) también tiene que estar optimizado por el
      // JIT antes de medir: sin este calentamiento, el primer test que se ejecuta sale varias veces más lento
      for (int i = 0; i < GLOBAL_WARMUP_ROUNDS; i++) {
        catalogService.getCategoryList();
        catalogService.getCategory("FISH");
        catalogService.getProduct("FI-SW-01");
        catalogService.getProductListByCategory("FISH");
        catalogService.getItemListByProduct("FI-SW-01");
        catalogService.getItem("EST-1");
        catalogService.isItemInStock("EST-1");
        catalogService.searchProductList("fish dog cat");
        accountService.getAccount("j2ee");
        accountService.getAccount("j2ee", "j2ee");
        accountService.insertAccount(newAccount());
        accountService.updateAccount(accountToUpdate);
        orderService.insertOrder(newOrder());
        orderService.getOrder(existingOrderId);
        orderService.getOrdersByUsername(ORDERS_USERNAME);
        orderService.getNextId("ordernum");
        newOrder();
      }
    } finally {
      monitor.enableCollection();
    }
  }

  /**
   * Muestra y guarda la tabla de resultados de JETM.
   *
   * @throws IOException
   *           si no se puede guardar el informe
   */
  @AfterAll
  void writeReport() throws IOException {
    JetmSupport.writeReport(monitor, "services");
  }

  /**
   * Listar las categorías.
   */
  @Test
  void getCategoryList() {
    String scenario = "catalog.getCategoryList()";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getCategoryList());

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver una categoría.
   */
  @Test
  void getCategory() {
    String scenario = "catalog.getCategory(FISH)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getCategory("FISH"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver un producto.
   */
  @Test
  void getProduct() {
    String scenario = "catalog.getProduct(FI-SW-01)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getProduct("FI-SW-01"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Listar los artículos de un producto.
   */
  @Test
  void getItemListByProduct() {
    String scenario = "catalog.getItemListByProduct(FI-SW-01)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getItemListByProduct("FI-SW-01"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Comprobar el stock de un artículo.
   */
  @Test
  void isItemInStock() {
    String scenario = "catalog.isItemInStock(EST-1)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.isItemInStock("EST-1"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Listar los productos de una categoría.
   */
  @Test
  void getProductListByCategory() {
    String scenario = "catalog.getProductListByCategory(FISH)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getProductListByCategory("FISH"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver un artículo.
   */
  @Test
  void getItem() {
    String scenario = "catalog.getItem(EST-1)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.getItem("EST-1"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Buscar productos con una palabra: una consulta a la base de datos por búsqueda.
   */
  @Test
  void searchProductListWithOneKeyword() {
    String scenario = "catalog.searchProductList(fish)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.searchProductList("fish"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertQueriesPerCall(1, scenario, "CatalogService.searchProductList", "ProductMapper.searchProductList");
  }

  /**
   * Buscar productos con tres palabras: el servicio hace una consulta por cada palabra.
   */
  @Test
  void searchProductListWithThreeKeywords() {
    String scenario = "catalog.searchProductList(fish dog cat)";
    JetmSupport.measure(monitor, scenario, () -> catalogService.searchProductList("fish dog cat"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertQueriesPerCall(3, scenario, "CatalogService.searchProductList", "ProductMapper.searchProductList");
  }

  /**
   * Iniciar sesión.
   */
  @Test
  void login() {
    String scenario = "account.getAccount(j2ee, j2ee)";
    JetmSupport.measure(monitor, scenario, () -> accountService.getAccount("j2ee", "j2ee"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Consultar una cuenta por su usuario.
   */
  @Test
  void getAccountByUsername() {
    String scenario = "account.getAccount(j2ee)";
    JetmSupport.measure(monitor, scenario, () -> accountService.getAccount("j2ee"));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Registrar una cuenta: escribe en las tablas account, profile y signon.
   */
  @Test
  void insertAccount() {
    String scenario = "account.insertAccount()";
    JetmSupport.measure(monitor, scenario, () -> accountService.insertAccount(newAccount()));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertQueriesPerCall(1, scenario, "AccountService.insertAccount", "AccountMapper.insertAccount");
    assertQueriesPerCall(1, scenario, "AccountService.insertAccount", "AccountMapper.insertProfile");
    assertQueriesPerCall(1, scenario, "AccountService.insertAccount", "AccountMapper.insertSignon");
  }

  /**
   * Modificar una cuenta: actualiza las tablas account, profile y signon.
   */
  @Test
  void updateAccount() {
    String scenario = "account.updateAccount()";
    JetmSupport.measure(monitor, scenario, () -> accountService.updateAccount(accountToUpdate));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertQueriesPerCall(1, scenario, "AccountService.updateAccount", "AccountMapper.updateAccount");
    assertQueriesPerCall(1, scenario, "AccountService.updateAccount", "AccountMapper.updateProfile");
    assertQueriesPerCall(1, scenario, "AccountService.updateAccount", "AccountMapper.updateSignon");
  }

  /**
   * Listar los pedidos de un usuario.
   */
  @Test
  void getOrdersByUsername() {
    String scenario = "order.getOrdersByUsername(" + ORDERS_PER_USERNAME + " orders)";
    JetmSupport.measure(monitor, scenario, () -> orderService.getOrdersByUsername(ORDERS_USERNAME));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Pedir el siguiente número de una secuencia: la lee y la actualiza.
   */
  @Test
  void getNextId() {
    String scenario = "order.getNextId(ordernum)";
    JetmSupport.measure(monitor, scenario, () -> orderService.getNextId("ordernum"));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertQueriesPerCall(1, scenario, "OrderService.getNextId", "SequenceMapper.getSequence");
    assertQueriesPerCall(1, scenario, "OrderService.getNextId", "SequenceMapper.updateSequence");
  }

  /**
   * Preparar un pedido a partir de la cuenta y del carrito: solo memoria, sin base de datos.
   */
  @Test
  void initOrder() {
    String scenario = "domain.initOrder(3 lines)";
    JetmSupport.measure(monitor, scenario, this::newOrder);

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Guardar un pedido de 3 líneas: escribe en varias tablas dentro de una transacción.
   */
  @Test
  void insertOrder() {
    String scenario = "order.insertOrder(3 lines)";
    JetmSupport.measure(monitor, scenario, () -> orderService.insertOrder(newOrder()));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertQueriesPerCall(1, scenario, "OrderService.insertOrder", "OrderMapper.insertOrder");
    assertQueriesPerCall(3, scenario, "OrderService.insertOrder", "ItemMapper.updateInventoryQuantity");
    assertQueriesPerCall(3, scenario, "OrderService.insertOrder", "LineItemMapper.insertLineItem");
  }

  /**
   * Leer un pedido de 3 líneas: el servicio hace 2 consultas más 2 por cada línea (problema N+1).
   */
  @Test
  void getOrder() {
    String scenario = "order.getOrder(3 lines)";
    JetmSupport.measure(monitor, scenario, () -> orderService.getOrder(existingOrderId));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertQueriesPerCall(1, scenario, "OrderService.getOrder", "OrderMapper.getOrder");
    assertQueriesPerCall(1, scenario, "OrderService.getOrder", "LineItemMapper.getLineItemsByOrderId");
    assertQueriesPerCall(3, scenario, "OrderService.getOrder", "ItemMapper.getItem");
    assertQueriesPerCall(3, scenario, "OrderService.getOrder", "ItemMapper.getInventoryQuantity");
  }

  private Order newOrder() {
    Order order = new Order();
    order.initOrder(account, cart);
    return order;
  }

  private Account newAccount() {
    Account created = new Account();
    created.setUsername("perf-" + newAccountCount++);
    created.setPassword("perf");
    created.setEmail("perf@example.com");
    created.setFirstName("Perf");
    created.setLastName("Test");
    created.setStatus("OK");
    created.setAddress1("Address 1");
    created.setAddress2("Address 2");
    created.setCity("City");
    created.setState("ST");
    created.setZip("12345");
    created.setCountry("USA");
    created.setPhone("555-555-5555");
    created.setFavouriteCategoryId("FISH");
    created.setLanguagePreference("english");
    created.setListOption(true);
    created.setBannerOption(true);
    return created;
  }

  private void assertAverageBelow(String scenario, double maxAverageMs) {
    Aggregate result = JetmSupport.aggregate(monitor, scenario);
    assertThat(result.getMeasurements()).isEqualTo(MEASURED_ITERATIONS);
    assertThat(result.getAverage()).as("tiempo medio (ms) de %s", scenario).isLessThan(maxAverageMs);
  }

  private void assertQueriesPerCall(int expectedPerCall, String... path) {
    assertThat(JetmSupport.aggregate(monitor, path).getMeasurements()).as("llamadas a %s", String.join(" > ", path))
        .isEqualTo((long) expectedPerCall * MEASURED_ITERATIONS);
  }

  /**
   * Contexto de Spring: los servicios reales sobre la base de datos en memoria de los tests de los mappers, con
   * transacciones (como en {@code applicationContext.xml}) y con JETM enganchado a servicios y mappers
   * ({@link JetmProxyConfig}).
   */
  @Configuration
  @EnableTransactionManagement
  @Import({ MapperTestContext.class, JetmProxyConfig.class, CatalogService.class, AccountService.class,
      OrderService.class })
  static class Context {

    /**
     * Monitor de JETM: anidado, para ver qué consultas hace cada servicio, y con un temporizador de nanosegundos.
     *
     * @return el monitor
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    EtmMonitor etmMonitor() {
      return new NestedMonitor(new Java15NanoTimer(), new RootAggregator());
    }

  }

}
