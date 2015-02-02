package netbeanstypescript;

import java.util.List;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.netbeans.modules.csl.api.Error;

/**
 *
 * @author jeffrey
 */
public class TSParser extends Parser {

    private Result result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        TSService.INSTANCE.updateFile(snapshot);
        result = new ParserResult(snapshot) {
            @Override
            public List<? extends Error> getDiagnostics() {
                return TSService.INSTANCE.getDiagnostics(getSnapshot());
            }

            @Override
            protected void invalidate() {}
        };
    }

    @Override
    public Result getResult(Task task) throws ParseException {
        return result;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        //System.out.println("addChangeListener " + cl);
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        //System.out.println("removeChangeListener " + cl);
    }
}
