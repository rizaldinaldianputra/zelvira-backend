package com.zelvira.zelvira.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
@Slf4j
public class PricingFormulaEngine {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluates a dynamic math expression with variables.
     * Expression example: "(HPP + PROFIT_NOMINAL) / (1.0 - (ADMIN_FEE_PERCENT / 100.0) - (PROFIT_PERCENT / 100.0))"
     */
    public BigDecimal evaluate(String expressionString, Map<String, Object> variables) {
        if (expressionString == null || expressionString.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (variables != null) {
                variables.forEach((k, v) -> {
                    if (v instanceof BigDecimal) {
                        context.setVariable(k, ((BigDecimal) v).doubleValue());
                    } else if (v instanceof Number) {
                        context.setVariable(k, ((Number) v).doubleValue());
                    } else {
                        context.setVariable(k, v);
                    }
                });
            }

            Expression exp = parser.parseExpression(expressionString);
            Object result = exp.getValue(context);

            if (result instanceof Number) {
                double val = ((Number) result).doubleValue();
                if (Double.isNaN(val) || Double.isInfinite(val)) {
                    log.warn("Expression {} evaluated to NaN or Infinite", expressionString);
                    return BigDecimal.ZERO;
                }
                return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate formula expression '{}': {}", expressionString, e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Applies price rounding strategy.
     */
    public BigDecimal applyRounding(BigDecimal rawPrice, String rule) {
        if (rawPrice == null || rawPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        long val = rawPrice.setScale(0, RoundingMode.HALF_UP).longValue();
        if (rule == null) rule = "ROUND_UP_1000";

        switch (rule.toUpperCase()) {
            case "ROUND_UP_100": {
                long rem = val % 100;
                if (rem > 0) val += (100 - rem);
                return BigDecimal.valueOf(val);
            }
            case "ROUND_UP_500": {
                long rem = val % 500;
                if (rem > 0) val += (500 - rem);
                return BigDecimal.valueOf(val);
            }
            case "ROUND_UP_1000": {
                long rem = val % 1000;
                if (rem > 0) val += (1000 - rem);
                return BigDecimal.valueOf(val);
            }
            case "CHARM_900": {
                long rem = val % 1000;
                if (rem > 0) val += (1000 - rem);
                val -= 100; // e.g. 90000 -> 89900
                if (val <= 0) val = 900;
                return BigDecimal.valueOf(val);
            }
            case "CHARM_999": {
                long rem = val % 1000;
                if (rem > 0) val += (1000 - rem);
                val -= 1; // e.g. 90000 -> 89999
                return BigDecimal.valueOf(val);
            }
            case "NONE":
            default:
                return rawPrice.setScale(0, RoundingMode.HALF_UP);
        }
    }
}

