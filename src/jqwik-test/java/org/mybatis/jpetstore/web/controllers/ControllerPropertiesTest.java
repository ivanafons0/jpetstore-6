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
package org.mybatis.jpetstore.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.mybatis.jpetstore.domain.Account;
import org.mybatis.jpetstore.domain.Cart;
import org.mybatis.jpetstore.domain.Category;
import org.mybatis.jpetstore.domain.Item;
import org.mybatis.jpetstore.domain.Order;
import org.mybatis.jpetstore.domain.Product;
import org.mybatis.jpetstore.service.AccountService;
import org.mybatis.jpetstore.service.CatalogService;
import org.mybatis.jpetstore.service.OrderService;
import org.mybatis.jpetstore.web.controllers.AccountController.AccountSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Properties for controller routing and validation paths.
 */
class ControllerPropertiesTest {

  // =========================================================================
  // RootController Tests
  // =========================================================================

  @Property
  void rootAlwaysRedirectsToIndex(@ForAll @StringLength(min = 0, max = 20) String ignored) {
    assertThat(new RootController().redirectToHome()).isEqualTo("redirect:/index.html");
  }

  // =========================================================================
  // CatalogController Tests
  // =========================================================================

  @Property
  void catalogRejectsBlankSearches(@ForAll boolean ignored) {
    CatalogController controller = new CatalogController();
    CatalogService service = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", service);
    ExtendedModelMap model = new ExtendedModelMap();

    assertThat(controller.searchProducts("", model)).isEqualTo("common/Error");
    assertThat(model.asMap()).containsKey("message");
  }

  @Property
  void catalogViewMainReturnsCorrectView(@ForAll boolean ignored) {
    assertThat(new CatalogController().viewMain()).isEqualTo("catalog/Main");
  }

  @Property
  void catalogViewCategoryHandlesNullAndValidIds(@ForAll boolean isNull, @ForAll @AlphaChars @StringLength(min = 1, max = 10) String categoryId) {
    CatalogController controller = new CatalogController();
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();

    String id = isNull ? null : categoryId;
    if (!isNull) {
      when(catalogService.getProductListByCategory(id)).thenReturn(Collections.emptyList());
      when(catalogService.getCategory(id)).thenReturn(new Category());
    }

    assertThat(controller.viewCategory(id, model)).isEqualTo("catalog/Category");

    if (!isNull) {
      assertThat(model.asMap()).containsKeys("productList", "category");
    } else {
      assertThat(model.asMap()).doesNotContainKeys("productList", "category");
    }
  }

  @Property
  void catalogViewProductHandlesNullAndValidIds(@ForAll boolean isNull, @ForAll @AlphaChars @StringLength(min = 1, max = 10) String productId) {
    CatalogController controller = new CatalogController();
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();

    String id = isNull ? null : productId;
    if (!isNull) {
      when(catalogService.getItemListByProduct(id)).thenReturn(Collections.emptyList());
      when(catalogService.getProduct(id)).thenReturn(new Product());
    }

    assertThat(controller.viewProduct(id, model)).isEqualTo("catalog/Product");
    if (!isNull) assertThat(model.asMap()).containsKeys("itemList", "product");
  }

  @Property
  void catalogViewItemPopulatesModel(@ForAll @AlphaChars @StringLength(min = 1, max = 10) String itemId) {
    CatalogController controller = new CatalogController();
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();

    Item item = new Item();
    item.setProduct(new Product());
    when(catalogService.getItem(itemId)).thenReturn(item);

    assertThat(controller.viewItem(itemId, model)).isEqualTo("catalog/Item");
    assertThat(model.asMap()).containsKeys("item", "product");
  }

  @Property
  void catalogSearchProductsValidKeyword(@ForAll @AlphaChars @StringLength(min = 1, max = 10) String keyword) {
    CatalogController controller = new CatalogController();
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();

    when(catalogService.searchProductList(keyword.toLowerCase())).thenReturn(Collections.emptyList());
    assertThat(controller.searchProducts(keyword, model)).isEqualTo("catalog/SearchProducts");
    assertThat(model.asMap()).containsKey("productList");
  }

  // =========================================================================
  // CartController Tests
  // =========================================================================

  @Property
  void cartRejectsMissingItemIds(@ForAll @StringLength(min = 0, max = 3) String ignored) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    assertThat(controller.addItemToCart(null, session, model)).isEqualTo("common/Error");
    assertThat(controller.removeItemFromCart(" ", session, model)).isEqualTo("common/Error");
    assertThat(model.asMap()).containsKey("message");
  }

  @Property
  void cartViewCartCreatesNewCartIfMissing(@ForAll boolean ignored) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    when(session.getAttribute("cart")).thenReturn(null);
    assertThat(controller.viewCart(session, model)).isEqualTo("cart/Cart");
    verify(session).setAttribute(eq("cart"), any(Cart.class));
    assertThat(model.asMap()).containsKey("cart");
  }

  @Property
  void cartAddItemToCartLogic(@ForAll boolean inCart, @ForAll @AlphaChars @StringLength(min = 1, max = 5) String itemId) {
    CartController controller = new CartController();
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    Cart cart = new Cart();
    Item item = new Item();
    item.setItemId(itemId);

    if (inCart) cart.addItem(item, true);

    when(session.getAttribute("cart")).thenReturn(cart);
    when(catalogService.isItemInStock(itemId)).thenReturn(true);
    when(catalogService.getItem(itemId)).thenReturn(item);

    assertThat(controller.addItemToCart(itemId, session, model)).isEqualTo("cart/Cart");
    assertThat(cart.containsItemId(itemId)).isTrue();
  }

  @Property
  void cartRemoveItemFromCartLogic(@ForAll boolean itemExists, @ForAll @AlphaChars @StringLength(min = 1, max = 5) String itemId) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    Cart cart = new Cart();
    if (itemExists) {
      Item item = new Item();
      item.setItemId(itemId);
      cart.addItem(item, true);
    }

    when(session.getAttribute("cart")).thenReturn(cart);
    String view = controller.removeItemFromCart(itemId, session, model);

    if (itemExists) {
      assertThat(view).isEqualTo("cart/Cart");
    } else {
      assertThat(view).isEqualTo("common/Error");
      assertThat(model.asMap()).containsKey("message");
    }
  }

  @Property
  void cartUpdateQuantitiesHandlesValidInvalidAndZero(@ForAll @IntRange(min = 0, max = 10) int quantity) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    Cart cart = new Cart();
    Item item = new Item();
    item.setItemId("ITEM-1");
    cart.addItem(item, true);
    when(session.getAttribute("cart")).thenReturn(cart);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("ITEM-1")).thenReturn(String.valueOf(quantity));

    controller.updateCartQuantities(request, session, model);

    if (quantity < 1) {
      assertThat(cart.containsItemId("ITEM-1")).isFalse();
    } else {
      assertThat(cart.containsItemId("ITEM-1")).isTrue();
    }
  }

  @Property
  void cartUpdateQuantitiesIgnoresNumberFormatException(@ForAll boolean ignored) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    Cart cart = new Cart();
    Item item = new Item();
    item.setItemId("ITEM-1");
    cart.addItem(item, true);
    when(session.getAttribute("cart")).thenReturn(cart);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("ITEM-1")).thenReturn("invalid-number");

    controller.updateCartQuantities(request, session, model);
    assertThat(cart.containsItemId("ITEM-1")).isTrue();
  }

  @Property
  void cartCheckoutReturnsView(@ForAll boolean ignored) {
    CartController controller = new CartController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    when(session.getAttribute("cart")).thenReturn(new Cart());
    assertThat(controller.checkOut(session, model)).isEqualTo("cart/Checkout");
  }

  // =========================================================================
  // AccountController Tests
  // =========================================================================

  @Property
  void accountSignonFormsAndSignoff(@ForAll boolean ignored) {
    AccountController controller = new AccountController();
    HttpSession session = mock(HttpSession.class);

    assertThat(controller.signonForm()).isEqualTo("account/SignonForm");
    assertThat(controller.signoff(session)).isEqualTo("redirect:/catalog");
    verify(session).invalidate();
  }

  @Property
  void accountSignonValidAndInvalid(@ForAll boolean isValid, @ForAll @AlphaChars @StringLength(min=1, max=10) String user, @ForAll @AlphaChars @StringLength(min=1, max=10) String pass) {
    AccountController controller = new AccountController();
    AccountService accountService = mock(AccountService.class);
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "accountService", accountService);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    if (isValid) {
      Account acc = new Account();
      acc.setUsername(user);
      acc.setFavouriteCategoryId("DOGS");
      when(accountService.getAccount(user, pass)).thenReturn(acc);
      when(catalogService.getProductListByCategory("DOGS")).thenReturn(Collections.emptyList());

      assertThat(controller.signon(user, pass, session, model)).isEqualTo("redirect:/catalog");
      verify(session).setAttribute(eq("accountBean"), any(AccountSession.class));
      assertThat(acc.getPassword()).isNull();
    } else {
      when(accountService.getAccount(user, pass)).thenReturn(null);
      assertThat(controller.signon(user, pass, session, model)).isEqualTo("account/SignonForm");
      assertThat(model.asMap()).containsKey("message");
    }
  }

  @Property
  void accountCreationForms(@ForAll @AlphaChars @StringLength(min=1, max=10) String username) {
    AccountController controller = new AccountController();
    AccountService accountService = mock(AccountService.class);
    ReflectionTestUtils.setField(controller, "accountService", accountService);
    ExtendedModelMap model = new ExtendedModelMap();

    assertThat(controller.newAccountForm(model)).isEqualTo("account/NewAccountForm");
    assertThat(model.asMap()).containsKeys("languages", "categories", "account");

    Account acc = new Account();
    acc.setUsername(username);
    assertThat(controller.newAccount(acc)).isEqualTo("redirect:/catalog");
    verify(accountService).insertAccount(acc);
  }

  @Property
  void accountEditFormWithAndWithoutSession(@ForAll boolean hasSession) {
    AccountController controller = new AccountController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    if (hasSession) {
      Account acc = new Account();
      when(session.getAttribute("accountBean")).thenReturn(new AccountSession(acc, Collections.emptyList(), true));
    }
    assertThat(controller.editAccountForm(session, model)).isEqualTo("account/EditAccountForm");
    assertThat(model.asMap()).containsKeys("languages", "categories");
    if (hasSession) assertThat(model.asMap()).containsKey("account");
  }

  @Property
  void accountEditExecutesUpdateAndRefreshesSession(@ForAll boolean ignored) {
    AccountController controller = new AccountController();
    AccountService accountService = mock(AccountService.class);
    CatalogService catalogService = mock(CatalogService.class);
    ReflectionTestUtils.setField(controller, "accountService", accountService);
    ReflectionTestUtils.setField(controller, "catalogService", catalogService);
    HttpSession session = mock(HttpSession.class);

    Account acc = new Account();
    acc.setUsername("jdoe");
    Account updated = new Account();
    updated.setUsername("jdoe");
    updated.setFavouriteCategoryId("CATS");

    when(accountService.getAccount("jdoe")).thenReturn(updated);
    when(catalogService.getProductListByCategory("CATS")).thenReturn(Collections.emptyList());

    assertThat(controller.editAccount(acc, session)).isEqualTo("redirect:/account/edit");
    verify(accountService).updateAccount(acc);
    verify(session).setAttribute(eq("accountBean"), any(AccountSession.class));
  }

  @Property
  void accountSessionAuthenticationLogic(@ForAll boolean authenticated, @ForAll boolean hasAccount, @ForAll boolean hasUsername) {
    Account acc = null;
    if (hasAccount) {
      acc = new Account();
      if (hasUsername) acc.setUsername("user");
    }
    AccountSession accSession = new AccountSession(acc, Collections.emptyList(), authenticated);

    boolean expected = authenticated && hasAccount && hasUsername;
    assertThat(accSession.isAuthenticated()).isEqualTo(expected);
    assertThat(accSession.getAccount()).isEqualTo(acc);
    assertThat(accSession.getMyList()).isEmpty();
  }

  // =========================================================================
  // OrderController Tests
  // =========================================================================

  @Property
  void orderListOrdersRequiresAuth(@ForAll @IntRange(min = 0, max = 2) int authState) {
    // 0 = Null session, 1 = Unauthenticated, 2 = Authenticated
    OrderController controller = new OrderController();
    OrderService orderService = mock(OrderService.class);
    ReflectionTestUtils.setField(controller, "orderService", orderService);
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    AccountSession sessionAttr = null;
    if (authState > 0) {
      Account acc = new Account();
      acc.setUsername("testuser");
      sessionAttr = new AccountSession(acc, Collections.emptyList(), authState == 2);
    }

    when(session.getAttribute("accountBean")).thenReturn(sessionAttr);

    if (authState == 2) {
      when(orderService.getOrdersByUsername("testuser")).thenReturn(Collections.emptyList());
      assertThat(controller.listOrders(session, model)).isEqualTo("order/ListOrders");
      assertThat(model.asMap()).containsKey("orderList");
    } else {
      assertThat(controller.listOrders(session, model)).isEqualTo("common/Error");
    }
  }

  @Property
  void orderNewOrderFormRequiresAuthAndCart(@ForAll @IntRange(min = 0, max = 2) int authState, @ForAll boolean hasCart) {
    OrderController controller = new OrderController();
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    AccountSession sessionAttr = null;
    if (authState > 0) {
      Account acc = new Account();
      acc.setUsername("testuser");
      sessionAttr = new AccountSession(acc, Collections.emptyList(), authState == 2);
    }

    when(session.getAttribute("accountBean")).thenReturn(sessionAttr);
    when(session.getAttribute("cart")).thenReturn(hasCart ? new Cart() : null);

    String view = controller.newOrderForm(session, model);
    if (authState != 2) {
      assertThat(view).isEqualTo("redirect:/account");
    } else if (hasCart) {
      assertThat(view).isEqualTo("order/NewOrderForm");
      assertThat(model.asMap()).containsKeys("order", "creditCardTypes");
    } else {
      assertThat(view).isEqualTo("common/Error");
    }
  }

  @Property
  void orderNewOrderRouting(
      @ForAll boolean confirmed,
      @ForAll boolean shippingReq,
      @ForAll boolean hasSessionOrder,
      @ForAll boolean populateAllFields) {

    OrderController controller = new OrderController();
    OrderService orderService = mock(OrderService.class);
    ReflectionTestUtils.setField(controller, "orderService", orderService);
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    Order inputOrder = new Order();
    if (populateAllFields) {
      inputOrder.setCardType("Visa");
      inputOrder.setCreditCard("1234567890");
      inputOrder.setExpiryDate("12/26");
      inputOrder.setBillToFirstName("John");
      inputOrder.setBillToLastName("Doe");
      inputOrder.setBillAddress1("123 Main St");
      inputOrder.setBillAddress2("Apt 4B");
      inputOrder.setBillCity("Springfield");
      inputOrder.setBillState("IL");
      inputOrder.setBillZip("62701");
      inputOrder.setBillCountry("USA");
      inputOrder.setShipToFirstName("Jane");
      inputOrder.setShipToLastName("Smith");
      inputOrder.setShipAddress1("456 Elm St");
      inputOrder.setShipAddress2("Suite 200");
      inputOrder.setShipCity("Chicago");
      inputOrder.setShipState("IL");
      inputOrder.setShipZip("60601");
      inputOrder.setShipCountry("USA");
    }

    Order sessionOrder = hasSessionOrder ? new Order() : null;
    when(session.getAttribute("order")).thenReturn(sessionOrder);

    String view = controller.newOrder(inputOrder, shippingReq, confirmed, session, model);

    if (confirmed) {
      if (hasSessionOrder) {
        verify(orderService).insertOrder(sessionOrder);
        assertThat(view).isEqualTo("order/ViewOrder");
      } else {
        assertThat(view).isEqualTo("common/Error");
      }
    } else {
      if (shippingReq) {
        assertThat(view).isEqualTo("order/ShippingForm");
      } else {
        assertThat(view).isEqualTo("order/ConfirmOrder");
      }
      if (hasSessionOrder && populateAllFields) {
        assertThat(sessionOrder.getCardType()).isEqualTo("Visa");
        assertThat(sessionOrder.getCreditCard()).isEqualTo("1234567890");
        assertThat(sessionOrder.getShipCountry()).isEqualTo("USA");
      }
    }
  }

  @Property
  void orderViewOrderChecksOwnership(@ForAll @IntRange(min = 0, max = 2) int authState, @ForAll boolean isOwner) {
    OrderController controller = new OrderController();
    OrderService orderService = mock(OrderService.class);
    ReflectionTestUtils.setField(controller, "orderService", orderService);
    ExtendedModelMap model = new ExtendedModelMap();
    HttpSession session = mock(HttpSession.class);

    AccountSession sessionAttr = null;
    if (authState > 0) {
      Account acc = new Account();
      acc.setUsername("testuser");
      sessionAttr = new AccountSession(acc, Collections.emptyList(), authState == 2);
    }

    when(session.getAttribute("accountBean")).thenReturn(sessionAttr);

    if (authState != 2) {
      assertThat(controller.viewOrder(1, session, model)).isEqualTo("redirect:/account");
      return;
    }

    Order order = new Order();
    order.setUsername(isOwner ? "testuser" : "otheruser");
    when(orderService.getOrder(1)).thenReturn(order);

    String view = controller.viewOrder(1, session, model);
    if (isOwner) {
      assertThat(view).isEqualTo("order/ViewOrder");
      assertThat(model.asMap()).containsKey("order");
    } else {
      assertThat(view).isEqualTo("common/Error");
      assertThat(model.asMap()).containsKey("message");
    }
  }
}
