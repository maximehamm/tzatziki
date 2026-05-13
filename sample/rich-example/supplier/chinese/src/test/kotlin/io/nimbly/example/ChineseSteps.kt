/*
 * CUCUMBER +
 * Copyright (C) 2024  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 *
 * Licensed under the GNU General Public License v2 or later.
 */

package io.nimbly.example

import io.cucumber.java.zh_cn.假如
import io.cucumber.java.zh_cn.当
import io.cucumber.java.zh_cn.那么
import io.cucumber.datatable.DataTable
import org.junit.Assert

/**
 * Step definitions for the Chinese sample feature [Chinese.feature].
 *
 * Cucumber-jvm exposes per-language annotation packages: `io.cucumber.java.zh_cn` maps
 * `假如` to `@Given`, `当` to `@When`, `那么` to `@Then`. The bodies are intentionally
 * trivial — this module exists to validate the IDE behaviour (parsing, breakpoints,
 * highlights, PDF export) on a non-Latin feature file, not to test business logic.
 */
class ChineseSteps {

    // 场景: 创建空订单
    @假如("罗密欧想买一杯饮料")
    fun romeoWantsADrink() {
    }

    @当("罗密欧支付订单")
    fun romeoPaysOrder() {
    }

    @那么("订单中应有 {int} 杯鸡尾酒")
    fun orderShouldContainCocktails(count: Int) {
        Assert.assertEquals(1, count)
    }

    @假如("商店里有以下书籍")
    fun storeHasBooks(books: DataTable) {
        Assert.assertFalse(books.asLists().isEmpty())
    }

    // 场景大纲: 计算订单总价
    @假如("商品单价为 {int} 元")
    fun unitPriceIs(price: Int) {
    }

    @当("顾客购买 {int} 件")
    fun customerBuys(quantity: Int) {
    }

    @那么("总价应为 {int} 元")
    fun totalPriceShouldBe(total: Int) {
    }

    // 场景: 货币与特殊符号
    // `¥199.00` / `50%（半价）` are unquoted tokens — `{word}` (no-whitespace run)
    // matches; `{string}` would have required double quotes.
    @假如("价格显示为 {word}")
    fun priceIsDisplayedAs(price: String) {
    }

    @那么("折扣为 {word}")
    fun discountIs(discount: String) {
    }
}
