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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests kept separate from the repository's existing tests.
 */
class CartPropertiesTest {

  @Property
  void addingAnItemAccumulatesQuantityAndSubtotal(@ForAll @IntRange(min = 1, max = 100) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = itemWithPrice(priceInCents);
    Cart cart = new Cart();

    for (int index = 0; index < quantity; index++) {
      cart.addItem(item, true);
    }

    BigDecimal expectedTotal = item.getListPrice().multiply(BigDecimal.valueOf(quantity));
    assertThat(cart.getNumberOfItems()).isOne();
    assertThat(cart.getCartItemList().get(0).getQuantity()).isEqualTo(quantity);
    assertThat(cart.getCartItemList().get(0).getTotal()).isEqualByComparingTo(expectedTotal);
    assertThat(cart.getSubTotal()).isEqualByComparingTo(expectedTotal);
  }

  @Property
  void subtotalIsTheSumOfAllLineTotals(@ForAll @IntRange(min = 1, max = 20) int firstQuantity,
      @ForAll @IntRange(min = 1, max = 20) int secondQuantity,
      @ForAll @IntRange(min = 1, max = 100000) int firstPriceInCents,
      @ForAll @IntRange(min = 1, max = 100000) int secondPriceInCents) {
    Item firstItem = itemWithPrice(firstPriceInCents);
    Item secondItem = itemWithPrice(secondPriceInCents);
    secondItem.setItemId("second-item");
    Cart cart = new Cart();

    cart.addItem(firstItem, true);
    cart.setQuantityByItemId(firstItem.getItemId(), firstQuantity);
    cart.addItem(secondItem, true);
    cart.setQuantityByItemId(secondItem.getItemId(), secondQuantity);

    BigDecimal expectedTotal = firstItem.getListPrice().multiply(BigDecimal.valueOf(firstQuantity))
        .add(secondItem.getListPrice().multiply(BigDecimal.valueOf(secondQuantity)));
    assertThat(cart.getSubTotal()).isEqualByComparingTo(expectedTotal);
  }

  @Property
  void cartRemainsConsistentForLargeGeneratedQuantities(
      @ForAll @IntRange(min = 1, max = 1000) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = itemWithPrice(priceInCents);
    Cart cart = new Cart();

    for (int index = 0; index < quantity; index++) {
      cart.addItem(item, true);
    }

    assertThat(cart.getNumberOfItems()).isOne();
    assertThat(cart.getCartItemList().get(0).getQuantity()).isEqualTo(quantity);
    assertThat(cart.getSubTotal())
        .isEqualByComparingTo(item.getListPrice().multiply(BigDecimal.valueOf(quantity)));
  }

  @Property
  void changingAnItemPriceKeepsLineTotalAndSubtotalConsistent(
      @ForAll @IntRange(min = 1, max = 20) int quantity,
      @ForAll @IntRange(min = 1, max = 100000) int initialPriceInCents,
      @ForAll @IntRange(min = 1, max = 100000) int updatedPriceInCents) {
    Item item = itemWithPrice(initialPriceInCents);
    Cart cart = new Cart();
    cart.addItem(item, true);
    cart.setQuantityByItemId(item.getItemId(), quantity);

    item.setListPrice(BigDecimal.valueOf(updatedPriceInCents, 2));

    assertThat(cart.getCartItemList().get(0).getTotal())
        .isEqualByComparingTo(cart.getSubTotal());
  }

  @Property
  void settingQuantityNeverLeavesANegativeCartQuantity(
      @ForAll @IntRange(min = -100, max = -1) int invalidQuantity,
      @ForAll @IntRange(min = 1, max = 100000) int priceInCents) {
    Item item = itemWithPrice(priceInCents);
    Cart cart = new Cart();
    cart.addItem(item, true);

    cart.setQuantityByItemId(item.getItemId(), invalidQuantity);

    assertThat(cart.getCartItemList().get(0).getQuantity()).isGreaterThanOrEqualTo(0);
  }

  private static Item itemWithPrice(int priceInCents) {
    Item item = new Item();
    item.setItemId("generated-item");
    item.setListPrice(BigDecimal.valueOf(priceInCents, 2));
    return item;
  }
}
