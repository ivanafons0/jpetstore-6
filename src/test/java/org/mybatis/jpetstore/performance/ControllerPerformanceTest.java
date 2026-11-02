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
import java.util.List;

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
import org.mybatis.jpetstore.domain.Product;
import org.mybatis.jpetstore.mapper.MapperTestContext;
import org.mybatis.jpetstore.service.AccountService;
import org.mybatis.jpetstore.service.CatalogService;
import org.mybatis.jpetstore.service.OrderService;
import org.mybatis.jpetstore.web.controllers.AccountController;
import org.mybatis.jpetstore.web.controllers.AccountController.AccountSession;
import org.mybatis.jpetstore.web.controllers.CartController;
import org.mybatis.jpetstore.web.controllers.CatalogController;
import org.mybatis.jpetstore.web.controllers.OrderController;
import org.mybatis.jpetstore.web.controllers.RootController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import etm.core.aggregation.Aggregate;
import etm.core.aggregation.RootAggregator;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.NestedMonitor;
import etm.core.timer.Java15NanoTimer;

/**
 * Tests de rendimiento de los controladores con JETM.
 * <p>
 * Los controladores se llaman directamente, con petición, sesión y modelo simulados ({@code spring-test}), sobre los
 * servicios reales y la base de datos HSQLDB en memoria. Así se mide la traza completa de una acción del usuario:
 * controlador, servicio y consultas a la base de datos. Queda fuera lo que aporta el servidor (HTTP y pintado de las
 * JSP), que solo se podría medir con la aplicación desplegada.
 */
@Tag("performance")
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ControllerPerformanceTest.Context.class)
class ControllerPerformanceTest {

  /** Tiempo medio máximo (ms) de una acción de solo lectura. */
  private static final double MAX_AVERAGE_READ_MS = 5;

  /** Tiempo medio máximo (ms) de una acción que escribe en la base de datos. */
  private static final double MAX_AVERAGE_WRITE_MS = 10;

  /** Rondas de calentamiento global, con todas las acciones, antes de medir nada. */
  private static final int GLOBAL_WARMUP_ROUNDS = 500;

  /** Artículos del carrito de la sesión. */
  private static final String[] CART_ITEM_IDS = { "EST-1", "EST-2", "EST-3" };

  /** Usuario con un número fijo de pedidos, para medir las acciones de consulta. */
  private static final String READ_ONLY_USERNAME = "perf-web-read";

  /** Pedidos que se crean para ese usuario. */
  private static final int ORDERS_PER_USERNAME = 10;

  @Autowired
  private EtmMonitor monitor;

  @Autowired
  private RootController rootController;

  @Autowired
  private CatalogController catalogController;

  @Autowired
  private CartController cartController;

  @Autowired
  private AccountController accountController;

  @Autowired
  private OrderController orderController;

  @Autowired
  private AccountService accountService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private OrderService orderService;

  /** Sesión de un usuario identificado, con carrito: la de los escenarios de compra. */
  private MockHttpSession session;

  /** Sesión de un usuario con un número fijo de pedidos: la de los escenarios de consulta. */
  private MockHttpSession readOnlySession;

  /** Cuenta identificada en {@link #session}. */
  private Account sessionAccount;

  /** Cuenta que modifican los escenarios de edición. */
  private Account accountToEdit;

  /** Pedido ya guardado del usuario de solo lectura. */
  private int existingOrderId;

  /** Contador para que cada cuenta nueva tenga un usuario distinto. */
  private int newAccountCount;

  /**
   * Prepara las sesiones y los datos, y calienta todas las acciones, sin medir nada.
   */
  @BeforeAll
  void prepareDataAndWarmUp() {
    monitor.disableCollection();
    try {
      sessionAccount = accountService.getAccount("j2ee");
      session = signedOnSession(sessionAccount);

      Account readOnlyAccount = accountService.getAccount("j2ee");
      readOnlyAccount.setUsername(READ_ONLY_USERNAME);
      readOnlySession = signedOnSession(readOnlyAccount);
      for (int i = 0; i < ORDERS_PER_USERNAME; i++) {
        Order order = new Order();
        order.initOrder(readOnlyAccount, newCart());
        orderService.insertOrder(order);
        existingOrderId = order.getOrderId();
      }

      accountToEdit = newAccountData();
      accountService.insertAccount(accountToEdit);

      for (int i = 0; i < GLOBAL_WARMUP_ROUNDS; i++) {
        rootController.redirectToHome();
        catalogController.viewMain();
        catalogController.viewCategory("FISH", model());
        catalogController.viewProduct("FI-SW-01", model());
        catalogController.viewItem("EST-1", model());
        catalogController.searchProducts("fish", model());
        cartController.viewCart(session, model());
        cartController.addItemToCart("EST-1", session, model());
        cartController.updateCartQuantities(updateRequest(), session, model());
        cartController.checkOut(session, model());
        accountController.signonForm();
        accountController.signon("j2ee", "j2ee", new MockHttpSession(), model());
        accountController.newAccountForm(model());
        accountController.newAccount(newAccountData());
        accountController.editAccountForm(session, model());
        accountController.editAccount(accountToEdit, new MockHttpSession());
        orderController.listOrders(readOnlySession, model());
        orderController.viewOrder(existingOrderId, readOnlySession, model());
        orderController.newOrderForm(session, model());
        orderController.newOrder(new Order(), false, false, session, model());
        orderController.newOrder(new Order(), false, true, session, model());
        restoreSessionOrder();
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
    JetmSupport.writeReport(monitor, "controllers");
  }

  /**
   * Página de inicio: solo redirige.
   */
  @Test
  void redirectToHome() {
    String scenario = "GET /";
    JetmSupport.measure(monitor, scenario, () -> rootController.redirectToHome());

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Portada del catálogo.
   */
  @Test
  void viewMain() {
    String scenario = "GET /catalog";
    JetmSupport.measure(monitor, scenario, () -> catalogController.viewMain());

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver una categoría.
   */
  @Test
  void viewCategory() {
    String scenario = "GET /catalog/category?categoryId=FISH";
    JetmSupport.measure(monitor, scenario, () -> catalogController.viewCategory("FISH", model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertCallsPerRequest(1, scenario, "CatalogController.viewCategory", "CatalogService.getCategory");
    assertCallsPerRequest(1, scenario, "CatalogController.viewCategory", "CatalogService.getProductListByCategory");
  }

  /**
   * Ver un producto.
   */
  @Test
  void viewProduct() {
    String scenario = "GET /catalog/product?productId=FI-SW-01";
    JetmSupport.measure(monitor, scenario, () -> catalogController.viewProduct("FI-SW-01", model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver un artículo.
   */
  @Test
  void viewItem() {
    String scenario = "GET /catalog/item?itemId=EST-1";
    JetmSupport.measure(monitor, scenario, () -> catalogController.viewItem("EST-1", model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Buscar productos.
   */
  @Test
  void searchProducts() {
    String scenario = "GET /catalog/search?keyword=fish";
    JetmSupport.measure(monitor, scenario, () -> catalogController.searchProducts("fish", model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver el carrito: no toca la base de datos, solo la sesión.
   */
  @Test
  void viewCart() {
    String scenario = "GET /cart";
    JetmSupport.measure(monitor, scenario, () -> cartController.viewCart(session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Añadir al carrito un artículo que ya está: solo incrementa la cantidad, sin consultas.
   */
  @Test
  void addItemToCart() {
    String scenario = "GET /cart/addItem?workingItemId=EST-1";
    JetmSupport.measure(monitor, scenario, () -> cartController.addItemToCart("EST-1", session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Quitar un artículo del carrito, volviéndolo a añadir entre medición y medición.
   */
  @Test
  void removeItemFromCart() {
    String scenario = "GET /cart/removeItem?workingItemId=EST-2";
    JetmSupport.measure(monitor, scenario, () -> cartController.removeItemFromCart("EST-2", session, model()),
        () -> cartController.addItemToCart("EST-2", session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Actualizar las cantidades del carrito.
   */
  @Test
  void updateCartQuantities() {
    String scenario = "POST /cart/update";
    JetmSupport.measure(monitor, scenario,
        () -> cartController.updateCartQuantities(updateRequest(), session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Pantalla de confirmación del carrito.
   */
  @Test
  void checkOut() {
    String scenario = "GET /cart/checkout";
    JetmSupport.measure(monitor, scenario, () -> cartController.checkOut(session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Formulario de identificación.
   */
  @Test
  void signonForm() {
    String scenario = "GET /account";
    JetmSupport.measure(monitor, scenario, () -> accountController.signonForm());

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Identificarse: comprueba la contraseña y carga la lista de productos favoritos.
   */
  @Test
  void signon() {
    String scenario = "POST /account/signon";
    JetmSupport.measure(monitor, scenario,
        () -> accountController.signon("j2ee", "j2ee", new MockHttpSession(), model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertCallsPerRequest(1, scenario, "AccountController.signon", "AccountService.getAccount");
    assertCallsPerRequest(1, scenario, "AccountController.signon", "CatalogService.getProductListByCategory");
  }

  /**
   * Cerrar la sesión: se mide sobre una sesión nueva, porque la invalida.
   */
  @Test
  void signoff() {
    String scenario = "GET /account/signoff";
    JetmSupport.measure(monitor, scenario, () -> accountController.signoff(new MockHttpSession()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Formulario de alta: no toca la base de datos.
   */
  @Test
  void newAccountForm() {
    String scenario = "GET /account/new";
    JetmSupport.measure(monitor, scenario, () -> accountController.newAccountForm(model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Registrar una cuenta.
   */
  @Test
  void newAccount() {
    String scenario = "POST /account/new";
    JetmSupport.measure(monitor, scenario, () -> accountController.newAccount(newAccountData()));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertCallsPerRequest(1, scenario, "AccountController.newAccount", "AccountService.insertAccount");
  }

  /**
   * Formulario de edición: lee la cuenta de la sesión, sin consultas.
   */
  @Test
  void editAccountForm() {
    String scenario = "GET /account/edit";
    JetmSupport.measure(monitor, scenario, () -> accountController.editAccountForm(session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Modificar una cuenta: actualiza, vuelve a leerla y recarga la lista de favoritos.
   */
  @Test
  void editAccount() {
    String scenario = "POST /account/edit";
    JetmSupport.measure(monitor, scenario, () -> accountController.editAccount(accountToEdit, new MockHttpSession()));

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertCallsPerRequest(1, scenario, "AccountController.editAccount", "AccountService.updateAccount");
    assertCallsPerRequest(1, scenario, "AccountController.editAccount", "AccountService.getAccount");
  }

  /**
   * Listar los pedidos del usuario.
   */
  @Test
  void listOrders() {
    String scenario = "GET /order/list (" + ORDERS_PER_USERNAME + " orders)";
    JetmSupport.measure(monitor, scenario, () -> orderController.listOrders(readOnlySession, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Ver un pedido: es la acción de lectura más costosa, por el problema N+1 de {@code OrderService.getOrder}.
   */
  @Test
  void viewOrder() {
    String scenario = "GET /order/view?orderId";
    JetmSupport.measure(monitor, scenario, () -> orderController.viewOrder(existingOrderId, readOnlySession, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
    assertCallsPerRequest(3, scenario, "OrderController.viewOrder", "OrderService.getOrder", "ItemMapper.getItem");
  }

  /**
   * Formulario de pedido: prepara el pedido a partir de la cuenta y del carrito, en memoria.
   */
  @Test
  void newOrderForm() {
    String scenario = "GET /order/new";
    JetmSupport.measure(monitor, scenario, () -> orderController.newOrderForm(session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Paso intermedio del pedido (sin confirmar): solo mueve datos entre la sesión y el modelo.
   */
  @Test
  void newOrderWithoutConfirmation() {
    String scenario = "POST /order/new (confirmed=false)";
    JetmSupport.measure(monitor, scenario, () -> orderController.newOrder(new Order(), false, false, session, model()));

    assertAverageBelow(scenario, MAX_AVERAGE_READ_MS);
  }

  /**
   * Confirmar el pedido: es la acción que escribe en la base de datos.
   */
  @Test
  void newOrderConfirmed() {
    String scenario = "POST /order/new (confirmed=true)";
    JetmSupport.measure(monitor, scenario, () -> orderController.newOrder(new Order(), false, true, session, model()),
        this::restoreSessionOrder);

    assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
    assertCallsPerRequest(1, scenario, "OrderController.newOrder", "OrderService.insertOrder");
  }

  /**
   * Deja en la sesión un pedido pendiente y el carrito que vacía la confirmación, para que cada medición parta del
   * mismo estado.
   */
  private void restoreSessionOrder() {
    session.setAttribute("cart", newCart());
    Order order = new Order();
    order.initOrder(sessionAccount, newCart());
    session.setAttribute("order", order);
  }

  private MockHttpSession signedOnSession(Account account) {
    MockHttpSession newSession = new MockHttpSession();
    List<Product> myList = catalogService.getProductListByCategory(account.getFavouriteCategoryId());
    newSession.setAttribute("accountBean", new AccountSession(account, myList, true));
    newSession.setAttribute("cart", newCart());
    return newSession;
  }

  private Cart newCart() {
    Cart cart = new Cart();
    for (String itemId : CART_ITEM_IDS) {
      cart.addItem(catalogService.getItem(itemId), true);
    }
    return cart;
  }

  private Account newAccountData() {
    Account created = new Account();
    created.setUsername("perf-web-" + newAccountCount++);
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

  private static Model model() {
    return new ExtendedModelMap();
  }

  private MockHttpServletRequest updateRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    for (String itemId : CART_ITEM_IDS) {
      request.setParameter(itemId, "2");
    }
    return request;
  }

  private void assertAverageBelow(String scenario, double maxAverageMs) {
    Aggregate result = JetmSupport.aggregate(monitor, scenario);
    assertThat(result.getMeasurements()).isEqualTo(MEASURED_ITERATIONS);
    assertThat(result.getAverage()).as("tiempo medio (ms) de %s", scenario).isLessThan(maxAverageMs);
  }

  private void assertCallsPerRequest(int expectedPerRequest, String... path) {
    assertThat(JetmSupport.aggregate(monitor, path).getMeasurements()).as("llamadas a %s", String.join(" > ", path))
        .isEqualTo((long) expectedPerRequest * MEASURED_ITERATIONS);
  }

  /**
   * Contexto de Spring: los controladores y servicios reales sobre la base de datos en memoria de los tests de los
   * mappers, con transacciones y con JETM enganchado a las tres capas ({@link JetmProxyConfig}).
   */
  @Configuration
  @EnableTransactionManagement
  @Import({ MapperTestContext.class, JetmProxyConfig.class, CatalogService.class, AccountService.class,
      OrderService.class, RootController.class, CatalogController.class, CartController.class, AccountController.class,
      OrderController.class })
  static class Context {

    /**
     * Monitor de JETM: anidado, para ver la traza completa de cada acción.
     *
     * @return el monitor
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    EtmMonitor etmMonitor() {
      return new NestedMonitor(new Java15NanoTimer(), new RootAggregator());
    }

  }

}
