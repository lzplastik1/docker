package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * 把 WebView 的打印内容直接写成 A4 PDF 文件（不弹打印界面）。
 * 放在 android.print 包里，才能创建 LayoutResultCallback / WriteResultCallback。
 */
public class PdfExporter {
    public interface Done {
        void done(boolean ok);
    }

    public static void export(final PrintDocumentAdapter adapter, final File out, final Done cb) {
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        adapter.onStart();
        adapter.onLayout(null, attrs, new CancellationSignal(), new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                final ParcelFileDescriptor fd;
                try {
                    fd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                } catch (Exception e) {
                    adapter.onFinish();
                    cb.done(false);
                    return;
                }
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, fd, new CancellationSignal(),
                        new PrintDocumentAdapter.WriteResultCallback() {
                            @Override
                            public void onWriteFinished(PageRange[] pages) {
                                close(fd);
                                adapter.onFinish();
                                cb.done(out.length() > 0);
                            }

                            @Override
                            public void onWriteFailed(CharSequence error) {
                                close(fd);
                                adapter.onFinish();
                                cb.done(false);
                            }

                            @Override
                            public void onWriteCancelled() {
                                close(fd);
                                adapter.onFinish();
                                cb.done(false);
                            }
                        });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                adapter.onFinish();
                cb.done(false);
            }

            @Override
            public void onLayoutCancelled() {
                adapter.onFinish();
                cb.done(false);
            }
        }, null);
    }

    private static void close(ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (Exception ignored) {
        }
    }
}
