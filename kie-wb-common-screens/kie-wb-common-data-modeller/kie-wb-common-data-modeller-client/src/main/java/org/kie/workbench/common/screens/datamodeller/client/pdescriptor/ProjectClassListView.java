/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.screens.datamodeller.client.pdescriptor;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.view.client.AsyncDataProvider;

public interface ProjectClassListView
        extends IsWidget {

    interface Presenter {

        void onLoadClasses();

        void onRemoveClass( ClassRow classRow );

        void addLoadClassesHandler( LoadClassesHandler loadClassesHandler );
    }

    interface LoadClassesHandler {
        void onLoadClasses();
    }

    void setPresenter( Presenter presenter );

    void setReadOnly( boolean readOnly );

    void setDataProvider( AsyncDataProvider<ClassRow> dataProvider );

    void redraw();

}
