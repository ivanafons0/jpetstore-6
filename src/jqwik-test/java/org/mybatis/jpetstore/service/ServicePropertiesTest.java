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
package org.mybatis.jpetstore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.mybatis.jpetstore.domain.Account;
import org.mybatis.jpetstore.domain.Item;
import org.mybatis.jpetstore.domain.LineItem;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.domain.Sequence;
import org.mybatis.jpetstore.mapper.AccountMapper;
import org.mybatis.jpetstore.mapper.CategoryMapper;
import org.mybatis.jpetstore.mapper.ItemMapper;
import org.mybatis.jpetstore.mapper.LineItemMapper;
import org.mybatis.jpetstore.mapper.OrderMapper;
import org.mybatis.jpetstore.mapper.ProductMapper;
import org.mybatis.jpetstore.mapper.SequenceMapper;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;
import net.jqwik.api.constraints.StringLength;

/**
 * Properties for service orchestration using public mapper contracts.
 */
class ServicePropertiesTest {

  // =========================================================================
  // AccountService Tests
  // =========================================================================

  @Property
  void accountServiceGetAccountByUsername(@ForAll @AlphaChars @StringLength(min=1, max=10) String username) {
    AccountMapper mapper = mock(AccountMapper.class);
    AccountService service = new AccountService(mapper);
    Account account = new Account();

    when(mapper.getAccountByUsername(username)).thenReturn(account);

    assertThat(service.getAccount(username)).isEqualTo(account);
  }

  @Property
  void accountServiceGetAccountByUsernameAndPassword(
      @ForAll @AlphaChars @StringLength(min=1, max=10) String username,
      @ForAll @AlphaChars @StringLength(min=1, max=10) String password) {
    AccountMapper mapper = mock(AccountMapper.class);
    AccountService service = new AccountService(mapper);
    Account account = new Account();

    when(mapper.getAccountByUsernameAndPassword(username, password)).thenReturn(account);

    assertThat(service.getAccount(username, password)).isEqualTo(account);
  }

  @Property
  void accountServiceInsertAccount(@ForAll boolean ignored) {
    AccountMapper mapper = mock(AccountMapper.class);
    AccountService service = new AccountService(mapper);
    Account account = new Account();

    service.insertAccount(account);

    verify(mapper).insertAccount(account);
    verify(mapper).insertProfile(account);
    verify(mapper).insertSignon(account);
  }

  @Property
  void accountUpdateOnlyChangesPasswordWhenNonEmpty(@ForAll @NotEmpty String password) {
    AccountMapper mapper = mock(AccountMapper.class);
    AccountService service = new AccountService(mapper);
    Account account = new Account();
    account.setPassword(password);

    service.updateAccount(account);

    verify(mapper).updateAccount(account);
    verify(mapper).updateProfile(account);
    verify(mapper).updateSignon(account);
  }

  @Property
  void accountUpdateSkipsPasswordWhenNullOrEmpty(@ForAll boolean isNull) {
    AccountMapper mapper = mock(AccountMapper.class);
    AccountService service = new AccountService(mapper);
    Account account = new Account();
    account.setPassword(isNull ? null : "");

    service.updateAccount(account);

    verify(mapper).updateAccount(account);
    verify(mapper).updateProfile(account);
    verify(mapper, times(0)).updateSignon(account);
  }

  // =========================================================================
  // CatalogService Tests
  // =========================================================================

  @Property
  void catalogSearchAddsOneMapperResultPerKeyword(
      @ForAll @NotEmpty @AlphaChars String first,
      @ForAll @NotEmpty @AlphaChars String second) {
    ProductMapper products = mock(ProductMapper.class);
    CatalogService service = new CatalogService(mock(CategoryMapper.class), mock(ItemMapper.class), products);

    when(products.searchProductList("%" + first.toLowerCase() + "%")).thenReturn(List.of());
    when(products.searchProductList("%" + second.toLowerCase() + "%")).thenReturn(List.of());

    assertThat(service.searchProductList(first + " " + second)).isEmpty();
    int firstCalls = first.equalsIgnoreCase(second) ? 2 : 1;
    verify(products, times(firstCalls)).searchProductList("%" + first.toLowerCase() + "%");
    verify(products, times(firstCalls)).searchProductList("%" + second.toLowerCase() + "%");
  }

  @Property
  void stockIsTrueExactlyForPositiveInventory(@ForAll @IntRange(min = -100, max = 100) int inventory) {
    ItemMapper items = mock(ItemMapper.class);
    CatalogService service = new CatalogService(mock(CategoryMapper.class), items, mock(ProductMapper.class));

    when(items.getInventoryQuantity("item")).thenReturn(inventory);

    assertThat(service.isItemInStock("item")).isEqualTo(inventory > 0);
  }

  @Property
  void catalogServiceDelegatesGetters(@ForAll @AlphaChars @StringLength(min=1, max=10) String id) {
    CategoryMapper categoryMapper = mock(CategoryMapper.class);
    ItemMapper itemMapper = mock(ItemMapper.class);
    ProductMapper productMapper = mock(ProductMapper.class);
    CatalogService service = new CatalogService(categoryMapper, itemMapper, productMapper);

    service.getCategoryList();
    verify(categoryMapper).getCategoryList();

    service.getCategory(id);
    verify(categoryMapper).getCategory(id);

    service.getProduct(id);
    verify(productMapper).getProduct(id);

    service.getProductListByCategory(id);
    verify(productMapper).getProductListByCategory(id);

    service.getItemListByProduct(id);
    verify(itemMapper).getItemListByProduct(id);

    service.getItem(id);
    verify(itemMapper).getItem(id);
  }

  // =========================================================================
  // OrderService Tests
  // =========================================================================

  @Property
  void nextIdAdvancesTheSequence(@ForAll @IntRange(min = 0, max = 100000) int current) {
    SequenceMapper sequences = mock(SequenceMapper.class);
    OrderService service = new OrderService(mock(ItemMapper.class), mock(OrderMapper.class), sequences,
        mock(LineItemMapper.class));

    when(sequences.getSequence(argThat(sequence -> sequence.getName().equals("orders")
        && sequence.getNextId() == -1))).thenReturn(new Sequence("orders", current));

    assertThat(service.getNextId("orders")).isEqualTo(current);
    verify(sequences).updateSequence(argThat(sequence -> sequence.getName().equals("orders")
        && sequence.getNextId() == current + 1));
  }

  @Property
  void nextIdThrowsExceptionWhenSequenceNotFound(@ForAll @AlphaChars @StringLength(min=1, max=10) String name) {
    SequenceMapper sequences = mock(SequenceMapper.class);
    OrderService service = new OrderService(mock(ItemMapper.class), mock(OrderMapper.class), sequences,
        mock(LineItemMapper.class));

    when(sequences.getSequence(any(Sequence.class))).thenReturn(null);

    assertThatThrownBy(() -> service.getNextId(name))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("could not get next " + name + " sequence");
  }

  @Property
  void orderServiceGetOrdersByUsername(@ForAll @AlphaChars @StringLength(min=1, max=10) String username) {
    OrderMapper orderMapper = mock(OrderMapper.class);
    OrderService service = new OrderService(mock(ItemMapper.class), orderMapper, mock(SequenceMapper.class), mock(LineItemMapper.class));

    service.getOrdersByUsername(username);

    verify(orderMapper).getOrdersByUsername(username);
  }

  @Property
  void orderServiceInsertOrder(
      @ForAll @IntRange(min = 1, max = 1000) int nextId,
      @ForAll @AlphaChars @StringLength(min=1, max=10) String itemId) {
    ItemMapper itemMapper = mock(ItemMapper.class);
    OrderMapper orderMapper = mock(OrderMapper.class);
    SequenceMapper sequenceMapper = mock(SequenceMapper.class);
    LineItemMapper lineItemMapper = mock(LineItemMapper.class);
    OrderService service = new OrderService(itemMapper, orderMapper, sequenceMapper, lineItemMapper);

    when(sequenceMapper.getSequence(any(Sequence.class))).thenReturn(new Sequence("ordernum", nextId));

    Order order = new Order();
    LineItem lineItem = new LineItem();
    lineItem.setItemId(itemId);
    lineItem.setQuantity(2);
    order.setLineItems(List.of(lineItem));

    service.insertOrder(order);

    assertThat(order.getOrderId()).isEqualTo(nextId);
    verify(itemMapper).updateInventoryQuantity(argThat(map ->
        map.get("itemId").equals(itemId) && map.get("increment").equals(2)
    ));
    verify(orderMapper).insertOrder(order);
    verify(orderMapper).insertOrderStatus(order);
    verify(lineItemMapper).insertLineItem(lineItem);
    assertThat(lineItem.getOrderId()).isEqualTo(nextId);
  }

  @Property
  void orderServiceGetOrder(
      @ForAll @IntRange(min = 1, max = 100) int orderId,
      @ForAll @AlphaChars @StringLength(min=1, max=10) String itemId) {
    ItemMapper itemMapper = mock(ItemMapper.class);
    OrderMapper orderMapper = mock(OrderMapper.class);
    SequenceMapper sequenceMapper = mock(SequenceMapper.class);
    LineItemMapper lineItemMapper = mock(LineItemMapper.class);
    OrderService service = new OrderService(itemMapper, orderMapper, sequenceMapper, lineItemMapper);

    Order order = new Order();
    when(orderMapper.getOrder(orderId)).thenReturn(order);

    LineItem lineItem = new LineItem();
    lineItem.setItemId(itemId);
    when(lineItemMapper.getLineItemsByOrderId(orderId)).thenReturn(List.of(lineItem));

    Item item = new Item();
    when(itemMapper.getItem(itemId)).thenReturn(item);
    when(itemMapper.getInventoryQuantity(itemId)).thenReturn(5);

    Order result = service.getOrder(orderId);

    assertThat(result).isEqualTo(order);
    assertThat(result.getLineItems()).hasSize(1);
    assertThat(lineItem.getItem()).isEqualTo(item);
    assertThat(item.getQuantity()).isEqualTo(5);
  }
}
