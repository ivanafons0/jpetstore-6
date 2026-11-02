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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.jpetstore.domain.Cart;
import org.mybatis.jpetstore.domain.Item;

import etm.core.aggregation.RootAggregator;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.FlatMonitor;
import etm.core.timer.Java15NanoTimer;

/**
 * Tests de rendimiento del carrito con JETM: cómo crece el tiempo de {@link Cart#getSubTotal()} con el número de líneas
 * del carrito.
 */
@Tag("performance")
class CartPerformanceTest {

  /** Tamaños de carrito medidos (nº de líneas distintas). */
  private static final int[] CART_SIZES = { 10, 100, 1_000, 10_000 };

  /** Tiempo medio máximo (ms) del subtotal de un carrito de 10.000 líneas (unas 20 veces lo medido: ~0,25 ms). */
  private static final double MAX_AVERAGE_MS_10000_LINES = 5;

  /**
   * Crecimiento máximo del tiempo al pasar de 1.000 a 10.000 líneas: un algoritmo lineal, O(n), tarda unas 10 veces
   * más; uno cuadrático, O(n²), unas 100.
   */
  private static final double MAX_GROWTH_FACTOR = 30;

  /** Tamaño del carrito con el que se miden el resto de operaciones. */
  private static final int OPERATIONS_CART_SIZE = 1_000;

  /** Tiempo medio máximo (ms) del resto de operaciones del carrito. */
  private static final double MAX_AVERAGE_MS_OPERATION = 1;

  /** El monitor de JETM. */
  private static EtmMonitor monitor;

  /** Guarda el último resultado para que el JIT no elimine el cálculo por no usarse. */
  private static volatile BigDecimal lastSubTotal;

  /**
   * Arranca el monitor de JETM: plano (sin anidamiento) y con un temporizador de nanosegundos.
   */
  @BeforeAll
  static void startMonitor() {
    monitor = new FlatMonitor(new Java15NanoTimer(), new RootAggregator());
    monitor.start();
  }

  /**
   * Muestra y guarda la tabla de resultados de JETM y para el monitor.
   *
   * @throws IOException
   *           si no se puede guardar el informe
   */
  @AfterAll
  static void writeReportAndStopMonitor() throws IOException {
    JetmSupport.writeReport(monitor, "cart");
    monitor.stop();
  }

  /**
   * El subtotal se calcula recorriendo todas las líneas: su tiempo debe crecer de forma lineal con el tamaño del
   * carrito y ser aceptable incluso con 10.000 líneas.
   */
  @Test
  void getSubTotalScalesLinearlyWithCartSize() {
    for (int cartSize : CART_SIZES) {
      Cart cart = cartWithLines(cartSize);
      JetmSupport.measure(monitor, pointName(cartSize), () -> lastSubTotal = cart.getSubTotal());
    }

    double average1000 = JetmSupport.aggregate(monitor, pointName(1_000)).getAverage();
    double average10000 = JetmSupport.aggregate(monitor, pointName(10_000)).getAverage();
    assertThat(average10000).as("tiempo medio (ms) con 10.000 líneas").isLessThan(MAX_AVERAGE_MS_10000_LINES);
    assertThat(average10000 / average1000).as("crecimiento del tiempo de 1.000 a 10.000 líneas")
        .isLessThan(MAX_GROWTH_FACTOR);
  }

  /**
   * El resto de operaciones del carrito sobre un carrito grande: las que usan el mapa interno son inmediatas y quitar
   * un producto recorre la lista, así que es la más costosa.
   */
  @Test
  void cartOperations() {
    Cart cart = cartWithLines(OPERATIONS_CART_SIZE);
    Item firstItem = cart.getCartItemList().get(0).getItem();
    String firstItemId = firstItem.getItemId();
    AtomicInteger newItems = new AtomicInteger(OPERATIONS_CART_SIZE);

    JetmSupport.measure(monitor, "Cart.containsItemId", () -> cart.containsItemId(firstItemId));
    JetmSupport.measure(monitor, "Cart.incrementQuantityByItemId", () -> cart.incrementQuantityByItemId(firstItemId));
    JetmSupport.measure(monitor, "Cart.setQuantityByItemId", () -> cart.setQuantityByItemId(firstItemId, 1));
    JetmSupport.measure(monitor, "Cart.removeItemById", () -> cart.removeItemById(firstItemId),
        () -> cart.addItem(firstItem, true));
    JetmSupport.measure(monitor, "Cart.addItem(new item)",
        () -> cart.addItem(newItem(newItems.getAndIncrement()), true));

    for (String pointName : new String[] { "Cart.containsItemId", "Cart.incrementQuantityByItemId",
        "Cart.setQuantityByItemId", "Cart.removeItemById", "Cart.addItem(new item)" }) {
      assertThat(JetmSupport.aggregate(monitor, pointName).getAverage()).as("tiempo medio (ms) de %s", pointName)
          .isLessThan(MAX_AVERAGE_MS_OPERATION);
    }
  }

  private static String pointName(int cartSize) {
    return "Cart.getSubTotal(" + cartSize + " lines)";
  }

  private static Cart cartWithLines(int numberOfLines) {
    Cart cart = new Cart();
    for (int i = 0; i < numberOfLines; i++) {
      cart.addItem(newItem(i), true);
    }
    return cart;
  }

  private static Item newItem(int number) {
    Item item = new Item();
    item.setItemId("ITEM-" + number);
    item.setListPrice(BigDecimal.valueOf(1000 + number, 2));
    return item;
  }

}
