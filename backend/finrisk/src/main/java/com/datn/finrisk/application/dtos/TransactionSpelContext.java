package com.datn.finrisk.application.dtos;

import com.datn.finrisk.core.entities.Transaction;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * Read-only projection of {@link Transaction} exposed to the SpEL rule engine
 * via the {@code #tx} context variable in
 * {@link com.datn.finrisk.core.services.RiskEvaluationService}.
 *
 * <p>Only the two fields that RuleService generates expressions for are
 * present — {@code amount} and {@code emotionSignal}. All JPA associations,
 * sensitive user data, and mutable state are intentionally excluded so that
 * a malicious or hand-crafted SpEL rule cannot traverse the Hibernate object
 * graph to reach credentials, tokens, or account balances.
 *
 * <p>Construction is restricted to the static factory {@link #from(Transaction)}
 * to guarantee that instances always reflect a real transaction snapshot.
 */
@Getter
public final class TransactionSpelContext {

    private final BigDecimal amount;
    private final String     emotionSignal;

    private TransactionSpelContext(BigDecimal amount, String emotionSignal) {
        this.amount        = amount;
        this.emotionSignal = emotionSignal;
    }

    /**
     * Creates a {@code TransactionSpelContext} from the given {@link Transaction}.
     * The two values are copied eagerly so the DTO holds no reference back to
     * the Hibernate session or any lazy-loaded association.
     */
    public static TransactionSpelContext from(Transaction tx) {
        return new TransactionSpelContext(
            tx.getAmount(),
            tx.getEmotionSignal()
        );
    }
}
