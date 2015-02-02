package netbeanstypescript;

import java.net.URL;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.indexing.Context;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexer;
import org.netbeans.modules.parsing.spi.indexing.CustomIndexerFactory;
import org.netbeans.modules.parsing.spi.indexing.Indexable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * This "indexer" doesn't really index anything, it's just a way to read all the TS files in a
 * project and get notified when they're changed or deleted.
 * @author jeffrey
 */
public class TSIndexerFactory extends CustomIndexerFactory {

    @Override
    public boolean scanStarted(Context context) {
        return false;
    }

    @Override
    public CustomIndexer createIndexer() {
        return new CustomIndexer() {

            @Override
            protected void index(Iterable<? extends Indexable> itrbl, Context cntxt) {
                for (Indexable indxbl: itrbl) {
                    FileObject fo = cntxt.getRoot().getFileObject(indxbl.getRelativePath());
                    if (fo != null && "text/typescript".equals(FileUtil.getMIMEType(fo))) {
                        TSService.INSTANCE.addFile(Source.create(fo).createSnapshot(), indxbl, cntxt);
                    }
                }
            }
        };
    }

    @Override
    public void scanFinished(Context context) {
        TSService.INSTANCE.scanFinished(context);
    }

    @Override
    public boolean supportsEmbeddedIndexers() {
        return false;
    }

    @Override
    public void filesDeleted(Iterable<? extends Indexable> itrbl, Context cntxt) {
        for (Indexable i: itrbl) {
            TSService.INSTANCE.removeFile(i, cntxt);
        }
    }

    @Override
    public void filesDirty(Iterable<? extends Indexable> itrbl, Context cntxt) {
        /*for (Indexable i: itrbl) {
            System.out.println("filesDirty: " + i.toString());
        }*/
    }

    @Override
    public void rootsRemoved(Iterable<? extends URL> removedRoots) {
        for (URL url: removedRoots) {
            TSService.INSTANCE.removeProgram(url);
        }
    }

    @Override
    public String getIndexerName() {
        return "typescript";
    }

    @Override
    public int getIndexVersion() {
        return 0;
    }
}
