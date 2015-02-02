package netbeanstypescript;

import java.util.Set;
import org.netbeans.modules.csl.api.InstantRenamer;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author jeffrey
 */
public class TSInstantRenamer implements InstantRenamer {

    @Override
    public boolean isRenameAllowed(ParserResult info, int caretOffset, String[] explanationRetValue) {
        return true;
    }

    @Override
    public Set<OffsetRange> getRenameRegions(ParserResult info, int caretOffset) {
        FileObject file = info.getSnapshot().getSource().getFileObject();
        return TSService.INSTANCE.findOccurrences(file, caretOffset).keySet();
    }
}
