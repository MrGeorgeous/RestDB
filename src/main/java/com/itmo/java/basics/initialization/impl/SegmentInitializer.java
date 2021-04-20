package com.itmo.java.basics.initialization.impl;

import com.itmo.java.basics.exceptions.DatabaseException;
import com.itmo.java.basics.index.impl.SegmentIndex;
import com.itmo.java.basics.index.impl.SegmentOffsetInfoImpl;
import com.itmo.java.basics.initialization.InitializationContext;
import com.itmo.java.basics.initialization.Initializer;
import com.itmo.java.basics.logic.Database;
import com.itmo.java.basics.logic.DatabaseRecord;
import com.itmo.java.basics.logic.Segment;
import com.itmo.java.basics.logic.impl.SegmentImpl;
import com.itmo.java.basics.logic.io.DatabaseInputStream;

import java.io.FileInputStream;
import java.util.Optional;


public class SegmentInitializer implements Initializer {

    /**
     * Добавляет в контекст информацию об инициализируемом сегменте.
     * Составляет индекс сегмента
     * Обновляет инфу в индексе таблицы
     *
     * @param context контекст с информацией об инициализируемой бд и об окружении
     * @throws DatabaseException если в контексте лежит неправильный путь к сегменту, невозможно прочитать содержимое. Ошибка в содержании
     */
    @Override
    public void perform(InitializationContext context) throws DatabaseException {
        SegmentImpl segment = (SegmentImpl) SegmentImpl.initializeFromContext(context.currentSegmentContext());

        try {
            DatabaseInputStream dbStream = new DatabaseInputStream(new FileInputStream(context.currentSegmentContext().getSegmentPath().toString()));
            Optional<DatabaseRecord> record;
            long offset = 0;
            while (offset < context.currentSegmentContext().getCurrentSize()) {
                record = dbStream.readDbUnit();
                if (record.isPresent() && record.get().isValuePresented()) {
                    context.currentSegmentContext().getIndex().onIndexedEntityUpdated(new String(record.get().getKey()), new SegmentOffsetInfoImpl(offset));
                    context.currentTableContext().getTableIndex().onIndexedEntityUpdated(new String(record.get().getKey()), segment);
                    offset += record.get().size();
                }
            }
        } catch (Exception e) {
            throw new DatabaseException("Segment was found corrupted while initializing.", e);
        }

        context.currentTableContext().updateCurrentSegment(segment);

    }
}
