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
package org.mybatis.jpetstore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;

/**
 * Black-box properties for simple domain objects and line-item calculations.
 */
class DomainPropertiesTest {

  @Property
  void productAndCategoryIdsAreTrimmed(@ForAll @NotEmpty String productId,
      @ForAll @NotEmpty String categoryId) {
    Product product = new Product();
    Category category = new Category();
    product.setProductId(" " + productId + " ");
    category.setCategoryId(" " + categoryId + " ");

    assertThat(product.getProductId()).isEqualTo(productId.trim());
    assertThat(category.getCategoryId()).isEqualTo(categoryId.trim());
  }

  @Property
  void productAndCategoryPropertiesRoundTrip(@ForAll @NotEmpty String value) {
    Product product = new Product();
    product.setProductId(value);
    product.setCategoryId(value);
    product.setName(value);
    product.setDescription(value);
    Category category = new Category();
    category.setCategoryId(value);
    category.setName(value);
    category.setDescription(value);

    assertThat(product.getCategoryId()).isEqualTo(value);
    assertThat(product.getName()).isEqualTo(value);
    assertThat(product.getDescription()).isEqualTo(value);
    assertThat(product.toString()).isEqualTo(value);
    assertThat(category.getName()).isEqualTo(value);
    assertThat(category.getDescription()).isEqualTo(value);
    assertThat(category.toString()).isEqualTo(value.trim());
  }

  @Property
  void sequenceSupportsDefaultAndParameterizedConstruction(
      @ForAll @NotEmpty String name,
      @ForAll int nextId) {
    Sequence sequence = new Sequence();
    sequence.setName(name);
    sequence.setNextId(nextId);
    Sequence constructed = new Sequence(name, nextId);

    assertThat(sequence.getName()).isEqualTo(name);
    assertThat(sequence.getNextId()).isEqualTo(nextId);
    assertThat(constructed.getName()).isEqualTo(name);
    assertThat(constructed.getNextId()).isEqualTo(nextId);
  }

  @Property
  void itemPropertiesRoundTrip(@ForAll @NotEmpty String itemId,
      @ForAll @IntRange(min = -1000, max = 1000) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = new Item();
    BigDecimal price = BigDecimal.valueOf(priceInCents, 2);
    item.setItemId(itemId);
    item.setQuantity(quantity);
    item.setListPrice(price);

    // Inicializar Product para evitar NullPointerException al llamar a toString()
    Product product = new Product();
    product.setProductId("PROD-1");
    item.setProduct(product);

    assertThat(item.getItemId()).isEqualTo(itemId.trim());
    assertThat(item.getQuantity()).isEqualTo(quantity);
    assertThat(item.getListPrice()).isEqualByComparingTo(price);

    // Cobertura para el método toString() de Item, el cual concatena (itemId-productId)
    String expectedToString = "(" + itemId.trim() + "-PROD-1)";
    assertThat(item.toString()).isEqualTo(expectedToString);
  }

  @Property
  void itemOptionalPropertiesRoundTrip(@ForAll @NotEmpty String value,
      @ForAll int supplierId, @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = new Item();
    Product product = new Product();
    item.setProduct(product);
    item.setSupplierId(supplierId);
    item.setUnitCost(BigDecimal.valueOf(priceInCents, 2));
    item.setStatus(value);
    item.setAttribute1(value);
    item.setAttribute2(value);
    item.setAttribute3(value);
    item.setAttribute4(value);
    item.setAttribute5(value);

    assertThat(item.getProduct()).isSameAs(product);
    assertThat(item.getSupplierId()).isEqualTo(supplierId);
    assertThat(item.getUnitCost()).isEqualByComparingTo(BigDecimal.valueOf(priceInCents, 2));
    assertThat(item.getStatus()).isEqualTo(value);
    assertThat(item.getAttribute1()).isEqualTo(value);
    assertThat(item.getAttribute2()).isEqualTo(value);
    assertThat(item.getAttribute3()).isEqualTo(value);
    assertThat(item.getAttribute4()).isEqualTo(value);
    assertThat(item.getAttribute5()).isEqualTo(value);
  }

  @Property
  void lineItemConstructorAndProperties(
      @ForAll @IntRange(min = 1, max = 1000) int lineNumber,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {

    Item item = new Item();
    item.setItemId("ITM-1");
    item.setListPrice(BigDecimal.valueOf(priceInCents, 2));

    CartItem cartItem = new CartItem();
    cartItem.setItem(item);
    cartItem.setQuantity(1);

    // Prueba del constructor de LineItem con CartItem
    LineItem lineItem = new LineItem(lineNumber, cartItem);

    assertThat(lineItem.getLineNumber()).isEqualTo(lineNumber);
    assertThat(lineItem.getUnitPrice()).isEqualByComparingTo(item.getListPrice());

    // Prueba explícita del setter de UnitPrice
    BigDecimal newPrice = BigDecimal.valueOf(priceInCents + 100, 2);
    lineItem.setUnitPrice(newPrice);
    assertThat(lineItem.getUnitPrice()).isEqualByComparingTo(newPrice);
  }

  @Property
  void accountPropertiesRoundTrip(@ForAll @NotEmpty String value, @ForAll boolean listOption,
      @ForAll boolean bannerOption) {
    Account account = new Account();
    account.setUsername(value);
    account.setPassword(value);
    account.setEmail(value);
    account.setFirstName(value);
    account.setLastName(value);
    account.setStatus(value);
    account.setAddress1(value);
    account.setAddress2(value);
    account.setCity(value);
    account.setState(value);
    account.setZip(value);
    account.setCountry(value);
    account.setPhone(value);
    account.setFavouriteCategoryId(value);
    account.setLanguagePreference(value);
    account.setListOption(listOption);
    account.setBannerOption(bannerOption);
    account.setBannerName(value);

    assertThat(account.getUsername()).isEqualTo(value);
    assertThat(account.getPassword()).isEqualTo(value);
    assertThat(account.getEmail()).isEqualTo(value);
    assertThat(account.getFirstName()).isEqualTo(value);
    assertThat(account.getLastName()).isEqualTo(value);
    assertThat(account.getStatus()).isEqualTo(value);
    assertThat(account.getAddress1()).isEqualTo(value);
    assertThat(account.getAddress2()).isEqualTo(value);
    assertThat(account.getCity()).isEqualTo(value);
    assertThat(account.getState()).isEqualTo(value);
    assertThat(account.getZip()).isEqualTo(value);
    assertThat(account.getCountry()).isEqualTo(value);
    assertThat(account.getPhone()).isEqualTo(value);
    assertThat(account.getFavouriteCategoryId()).isEqualTo(value);
    assertThat(account.getLanguagePreference()).isEqualTo(value);
    assertThat(account.isListOption()).isEqualTo(listOption);
    assertThat(account.isBannerOption()).isEqualTo(bannerOption);
    assertThat(account.getBannerName()).isEqualTo(value);
  }

  @Property
  void lineItemTotalIsPriceTimesQuantity(@ForAll @IntRange(min = 0, max = 100) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = new Item();
    item.setItemId("generated");
    item.setListPrice(BigDecimal.valueOf(priceInCents, 2));
    LineItem lineItem = new LineItem();
    lineItem.setItem(item);
    lineItem.setQuantity(quantity);

    assertThat(lineItem.getTotal())
        .isEqualByComparingTo(item.getListPrice().multiply(BigDecimal.valueOf(quantity)));
  }

  @Property
  void orderCopiesAccountAndCartData(@ForAll @NotEmpty String value,
      @ForAll @IntRange(min = 1, max = 100) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Account account = new Account();
    account.setUsername(value);
    account.setFirstName(value);
    account.setLastName(value);
    account.setAddress1(value);
    account.setAddress2(value);
    account.setCity(value);
    account.setState(value);
    account.setZip(value);
    account.setCountry(value);
    Item item = new Item();
    item.setItemId("order-item");
    item.setListPrice(BigDecimal.valueOf(priceInCents, 2));
    Cart cart = new Cart();
    cart.addItem(item, true);
    cart.setQuantityByItemId(item.getItemId(), quantity);

    Order order = new Order();
    order.initOrder(account, cart);

    assertThat(order.getUsername()).isEqualTo(value);
    assertThat(order.getShipToFirstName()).isEqualTo(value);
    assertThat(order.getBillToFirstName()).isEqualTo(value);
    assertThat(order.getTotalPrice()).isEqualByComparingTo(cart.getSubTotal());
    assertThat(order.getLineItems()).hasSize(1);
    assertThat(order.getLineItems().get(0).getQuantity()).isEqualTo(quantity);
    assertThat(order.getFormattedOrderDate()).matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");
  }

  @Property
  void orderLineItemsCanBeReplacedAndAppended(@ForAll @IntRange(min = 0, max = 100) int orderId,
      @ForAll @IntRange(min = 0, max = 100) int lineNumber) {
    LineItem lineItem = new LineItem();
    lineItem.setOrderId(orderId);
    lineItem.setLineNumber(lineNumber);
    Order order = new Order();
    order.setLineItems(new ArrayList<>(java.util.Collections.singletonList(lineItem)));
    order.addLineItem(lineItem);

    assertThat(order.getLineItems()).containsExactly(lineItem, lineItem);
  }

  @Property
  void orderPropertiesRoundTrip(@ForAll @NotEmpty String value,
      @ForAll @IntRange(min = 0, max = 100) int orderId,
      @ForAll @IntRange(min = 1, max = 100000) int totalInCents) {
    LocalDateTime orderDate = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    BigDecimal total = BigDecimal.valueOf(totalInCents, 2);
    Order order = new Order();
    order.setOrderId(orderId);
    order.setUsername(value);
    order.setOrderDate(orderDate);
    order.setShipAddress1(value);
    order.setShipAddress2(value);
    order.setShipCity(value);
    order.setShipState(value);
    order.setShipZip(value);
    order.setShipCountry(value);
    order.setBillAddress1(value);
    order.setBillAddress2(value);
    order.setBillCity(value);
    order.setBillState(value);
    order.setBillZip(value);
    order.setBillCountry(value);
    order.setCourier(value);
    order.setTotalPrice(total);
    order.setBillToFirstName(value);
    order.setBillToLastName(value);
    order.setShipToFirstName(value);
    order.setShipToLastName(value);
    order.setCreditCard(value);
    order.setExpiryDate(value);
    order.setCardType(value);
    order.setLocale(value);
    order.setStatus(value);

    assertThat(order.getOrderId()).isEqualTo(orderId);
    assertThat(order.getUsername()).isEqualTo(value);
    assertThat(order.getOrderDate()).isEqualTo(orderDate);
    assertThat(order.getShipAddress1()).isEqualTo(value);
    assertThat(order.getShipAddress2()).isEqualTo(value);
    assertThat(order.getShipCity()).isEqualTo(value);
    assertThat(order.getShipState()).isEqualTo(value);
    assertThat(order.getShipZip()).isEqualTo(value);
    assertThat(order.getShipCountry()).isEqualTo(value);
    assertThat(order.getBillAddress1()).isEqualTo(value);
    assertThat(order.getBillAddress2()).isEqualTo(value);
    assertThat(order.getBillCity()).isEqualTo(value);
    assertThat(order.getBillState()).isEqualTo(value);
    assertThat(order.getBillZip()).isEqualTo(value);
    assertThat(order.getBillCountry()).isEqualTo(value);
    assertThat(order.getCourier()).isEqualTo(value);
    assertThat(order.getTotalPrice()).isEqualByComparingTo(total);
    assertThat(order.getBillToFirstName()).isEqualTo(value);
    assertThat(order.getBillToLastName()).isEqualTo(value);
    assertThat(order.getShipToFirstName()).isEqualTo(value);
    assertThat(order.getShipToLastName()).isEqualTo(value);
    assertThat(order.getCreditCard()).isEqualTo(value);
    assertThat(order.getExpiryDate()).isEqualTo(value);
    assertThat(order.getCardType()).isEqualTo(value);
    assertThat(order.getLocale()).isEqualTo(value);
    assertThat(order.getStatus()).isEqualTo(value);
    assertThat(order.getFormattedOrderDate()).isEqualTo("2026/01/02 03:04:05");
  }
}
