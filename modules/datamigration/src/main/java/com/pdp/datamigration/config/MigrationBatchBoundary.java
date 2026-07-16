package com.pdp.datamigration.config;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 迁移批次的本地事务边界：源读取先完成并释放连接，再在独立目标事务中幂等写入。
 */
public final class MigrationBatchBoundary {

    private final TransactionTemplate sourceReadTransaction;
    private final TransactionTemplate targetWriteTransaction;

    MigrationBatchBoundary(
            TransactionTemplate sourceReadTransaction,
            TransactionTemplate targetWriteTransaction) {
        this.sourceReadTransaction =
                Objects.requireNonNull(sourceReadTransaction, "sourceReadTransaction");
        this.targetWriteTransaction =
                Objects.requireNonNull(targetWriteTransaction, "targetWriteTransaction");
    }

    public <S, R> R execute(Supplier<S> sourceReader, Function<S, R> targetWriter) {
        Objects.requireNonNull(sourceReader, "sourceReader");
        Objects.requireNonNull(targetWriter, "targetWriter");
        S sourceBatch = sourceReadTransaction.execute(status -> sourceReader.get());
        return targetWriteTransaction.execute(status -> targetWriter.apply(sourceBatch));
    }
}
