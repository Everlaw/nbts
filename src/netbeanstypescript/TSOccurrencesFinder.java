package netbeanstypescript;

import java.util.Map;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.OccurrencesFinder;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;

/**
 *
 * @author jeffrey
 */
public class TSOccurrencesFinder extends OccurrencesFinder<Parser.Result> {

    private int caretPosition;
    private Map<OffsetRange, ColoringAttributes> result;

    @Override
    public void setCaretPosition(int pos) {
        caretPosition = pos;
    }

    @Override
    public Map<OffsetRange, ColoringAttributes> getOccurrences() {
        return result;
    }

    @Override
    public void run(Parser.Result t, SchedulerEvent se) {
        result = TSService.INSTANCE.findOccurrences(
                t.getSnapshot().getSource().getFileObject(), caretPosition);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.CURSOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {}
}
