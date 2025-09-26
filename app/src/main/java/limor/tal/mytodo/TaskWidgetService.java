package limor.tal.mytodo;

import android.content.Intent;
import android.widget.RemoteViewsService;

/**
 * Service for providing scrollable task data to the widget
 */
public class TaskWidgetService extends RemoteViewsService {
    
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TaskRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}
