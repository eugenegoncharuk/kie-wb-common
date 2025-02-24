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

package org.kie.workbench.common.screens.datamodeller.client;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import com.google.gwt.user.client.ui.IsWidget;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.kie.workbench.common.screens.datamodeller.client.pdescriptor.ClassRow;
import org.kie.workbench.common.screens.datamodeller.client.pdescriptor.ClassRowImpl;
import org.kie.workbench.common.screens.datamodeller.client.pdescriptor.PropertyRow;
import org.kie.workbench.common.screens.datamodeller.client.resources.i18n.Constants;
import org.kie.workbench.common.screens.datamodeller.client.type.PersistenceDescriptorType;
import org.kie.workbench.common.screens.datamodeller.model.persistence.PersistenceDescriptorEditorContent;
import org.kie.workbench.common.screens.datamodeller.model.persistence.PersistenceDescriptorModel;
import org.kie.workbench.common.screens.datamodeller.model.persistence.PersistenceUnitModel;
import org.kie.workbench.common.screens.datamodeller.model.persistence.Property;
import org.kie.workbench.common.screens.datamodeller.model.persistence.TransactionType;
import org.kie.workbench.common.screens.datamodeller.service.DataModelerService;
import org.kie.workbench.common.screens.datamodeller.service.PersistenceDescriptorEditorService;
import org.kie.workbench.common.screens.datamodeller.service.PersistenceDescriptorService;
import org.kie.workbench.common.widgets.client.resources.i18n.CommonConstants;
import org.kie.workbench.common.widgets.metadata.client.KieEditor;
import org.uberfire.backend.vfs.ObservablePath;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartTitleDecoration;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.ext.editor.commons.client.file.SaveOperationService;
import org.uberfire.ext.widgets.common.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.uberfire.ext.widgets.common.client.common.Page;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnFocus;
import org.uberfire.lifecycle.OnMayClose;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.ParameterizedCommand;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.workbench.model.menu.Menus;

@Dependent
@WorkbenchEditor( identifier = "PersistenceDescriptorEditor",
        supportedTypes = { PersistenceDescriptorType.class },
        priority = Integer.MAX_VALUE )
public class PersistenceDescriptorEditorPresenter
        extends KieEditor
        implements PersistenceDescriptorEditorView.Presenter {

    private PersistenceDescriptorEditorView view;

    @Inject
    private PersistenceDescriptorType type;

    @Inject
    private Caller<PersistenceDescriptorEditorService> editorService;

    @Inject
    private Caller<PersistenceDescriptorService> descriptorService;

    @Inject
    private Caller<DataModelerService> dataModelerService;

    private PersistenceDescriptorEditorContent content;

    private boolean createIfNotExists = false;

    @Inject
    public PersistenceDescriptorEditorPresenter( PersistenceDescriptorEditorView baseView ) {
        super( baseView );
        this.view = baseView;
        baseView.setPresenter( this );
    }

    @OnStartup
    public void onStartup( final ObservablePath path,
            final PlaceRequest place ) {
        this.createIfNotExists = "true".equals( place.getParameter( "createIfNotExists", null ) );
        init( path, place, type );
    }

    @OnFocus
    public void onFocus() {
        //for some reason the tables needs to be repainted when the view is restored.
        view.redraw();
    }

    @WorkbenchPartTitle
    public String getTitleText() {
        if ( versionRecordManager.getCurrentPath() != null ) {
            super.getTitleText();
        } else {
            //TODO check why I'm getting this case and remove after checked
            return type.getDescription();
        }
        return super.getTitleText();
    }

    @WorkbenchPartTitleDecoration
    public IsWidget getTitle() {
        return super.getTitle();
    }

    @WorkbenchPartView
    public IsWidget getView() {
        return super.getWidget();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        makeMenuBar();
        return super.menus;
    }

    @Override
    protected void loadContent() {
        editorService.call( getLoadModelSuccessCallback(),
                new HasBusyIndicatorDefaultErrorCallback( view ) ).loadContent(
                versionRecordManager.getCurrentPath(),
                createIfNotExists );
    }

    @OnMayClose
    public boolean onMayClose() {
        if ( isDirty( getContent().hashCode() ) ) {
            return view.confirmClose();
        }
        return true;
    }

    @OnClose
    public void OnClose() {
        super.OnClose();
    }

    private RemoteCallback<PersistenceDescriptorEditorContent> getLoadModelSuccessCallback() {

        return new RemoteCallback<PersistenceDescriptorEditorContent>() {
            @Override
            public void callback( PersistenceDescriptorEditorContent content ) {

                if ( versionRecordManager.getCurrentPath() == null ) {
                    return;
                }
                if ( content.isCreated() ) {
                    versionRecordManager.reloadVersions( content.getPath() );
                }

                resetEditorPages( content.getOverview() );
                addSourcePage();

                createOriginalHash( content );
                setContent( content );
                view.setReadOnly( isReadOnly );
                updateSource( content.getSource() );

                view.hideBusyIndicator();
            }
        };
    }

    private void setContent( PersistenceDescriptorEditorContent content ) {
        this.content = content;
        PersistenceDescriptorModel descriptorModel = content != null ? content.getDescriptorModel() : null;
        PersistenceUnitModel persistenceUnitModel = descriptorModel != null ? descriptorModel.getPersistenceUnit() : null;
        view.clear();
        if ( persistenceUnitModel != null ) {
            view.setPersistenceUnitName( persistenceUnitModel.getName() );
            view.setPersistenceProvider( persistenceUnitModel.getProvider() );
            view.setJTADataSource( persistenceUnitModel.getJtaDataSource() );

            view.setJTATransactions( false );
            view.setResourceLocalTransactions( false );

            if ( TransactionType.JTA.equals( persistenceUnitModel.getTransactionType() ) ) {
                view.setJTATransactions( true );
            } else if ( TransactionType.RESOURCE_LOCAL.equals( persistenceUnitModel.getTransactionType() ) ) {
                view.setResourceLocalTransactions( true );
                view.setResourceLocalTransactionsVisible( true );
                updateResourceLocalTransactionsAlert();
            }

            view.getPersistenceUnitProperties().setProperties( wrappPropertiesList( persistenceUnitModel.getProperties() ) );
            view.getPersistenceUnitClasses().setClasses( wrappClassesList( persistenceUnitModel.getClasses() ) );
        }
    }

    private PersistenceDescriptorEditorContent getContent() {
        return content;
    }

    protected void addSourcePage() {

        addPage( new Page( view.getSourceEditor(),
                CommonConstants.INSTANCE.SourceTabTitle() ) {
            @Override
            public void onFocus() {
                onSourceTabSelected();
            }

            @Override
            public void onLostFocus() {

            }

        } );
    }

    protected void updateSource(String source) {
        view.setSource( source );
    }

    protected void save() {
        updateContent();
        new SaveOperationService().save( versionRecordManager.getCurrentPath(),
                new ParameterizedCommand<String>() {
                    @Override
                    public void execute( final String commitMessage ) {
                        editorService.call( getSaveSuccessCallback( getContent().hashCode() ) ).save( versionRecordManager.getCurrentPath(),
                                getContent(),
                                metadata,
                                commitMessage );
                    }
                }
        );
        concurrentUpdateSessionInfo = null;
    }

    @Override
    public void onSourceTabSelected() {
        updateContent();
        PersistenceDescriptorModel persistenceDescriptor = getContent().getDescriptorModel();
        if ( persistenceDescriptor != null ) {
            view.showBusyIndicator( Constants.INSTANCE.persistence_descriptor_editor_loading_source_message() );
            descriptorService.call( new RemoteCallback<String>() {
                                        @Override
                                        public void callback( String source ) {
                                            view.hideBusyIndicator();
                                            updateSource( source );
                                        }
                                    }, new HasBusyIndicatorDefaultErrorCallback( view )
            ).toSource( versionRecordManager.getCurrentPath(), persistenceDescriptor );
        }
    }

    //Presenter methods
    @Override
    public void onPersistenceUnitNameChanged( String persistenceUnitName ) {
        ensurePersistenceUnit();
        getContent().getDescriptorModel().getPersistenceUnit().setName( persistenceUnitName );
    }

    @Override
    public void onPersistenceProviderChanged( String provider ) {
        ensurePersistenceUnit();
        getContent().getDescriptorModel().getPersistenceUnit().setProvider( provider );
    }

    @Override
    public void onJTADataSourceChanged( String jtaDataSource ) {
        ensurePersistenceUnit();
        getContent().getDescriptorModel().getPersistenceUnit().setJtaDataSource( jtaDataSource );
    }

    @Override
    public void onJTATransactionsChanged() {
        ensurePersistenceUnit();
        getContent().getDescriptorModel().getPersistenceUnit().setTransactionType(
                view.getJTATransactions() ? TransactionType.JTA : TransactionType.RESOURCE_LOCAL
        );
        updateResourceLocalTransactionsAlert();
    }

    @Override
    public void onResourceLocalTransactionsChanged() {
        ensurePersistenceUnit();
        getContent().getDescriptorModel().getPersistenceUnit().setTransactionType(
            view.getResourceLocalTransactions() ? TransactionType.RESOURCE_LOCAL : TransactionType.JTA
        );
        updateResourceLocalTransactionsAlert();
    }

    private void updateResourceLocalTransactionsAlert( ) {
        if ( view.getResourceLocalTransactions() ) {
            view.setTransactionTypeHelpMessage( Constants.INSTANCE.persistence_descriptor_editor_resource_local_transactions_not_supported_message() );
        } else {
            view.setTransactionTypeHelpMessage( null );
        }
    }

    @Override
    public void onLoadClasses() {
        view.showBusyIndicator( Constants.INSTANCE.persistence_descriptor_editor_loading_classes_message() );
        dataModelerService.call( getLoadClassesSuccessCallback(),
                new HasBusyIndicatorDefaultErrorCallback( view ) ).findPersistableClasses( versionRecordManager.getCurrentPath() );
    }

    protected void updateContent() {
        ensurePersistenceUnit();
        content.getDescriptorModel().getPersistenceUnit().setProperties(
                unWrappPropertiesList( view.getPersistenceUnitProperties().getProperties() ) );
        content.getDescriptorModel().getPersistenceUnit().setClasses(
                unWrappClassesList( view.getPersistenceUnitClasses().getClasses() ) );
    }

    private RemoteCallback<List<String>> getLoadClassesSuccessCallback() {
        return new RemoteCallback<List<String>>() {
            @Override
            public void callback( List<String> classes ) {
                view.hideBusyIndicator();
                view.getPersistenceUnitClasses().setClasses( wrappClassesList( classes ) );
            }
        };
    }

    private void ensurePersistenceUnit() {
        if ( getContent().getDescriptorModel().getPersistenceUnit() == null ) {
            getContent().getDescriptorModel().setPersistenceUnit( new PersistenceUnitModel() );
        }
    }

    private List<ClassRow> wrappClassesList( List<String> classes ) {
        List<ClassRow> classRows = new ArrayList<ClassRow>(  );
        if ( classes == null ) return null;
        for ( String clazz : classes ) {
            classRows.add( new ClassRowImpl( clazz ) );
        }
        return classRows;
    }

    private List<String> unWrappClassesList( List<ClassRow> classRows ) {
        List<String> classes = new ArrayList<String>(  );
        if ( classRows == null ) return null;
        for ( ClassRow classRow : classRows ) {
            classes.add( classRow.getClassName() );
        }
        return classes;
    }

    private List<PropertyRow> wrappPropertiesList( List<Property> properties ) {
        List<PropertyRow> wrapperList = new ArrayList<PropertyRow>(  );
        if ( properties == null ) return null;
        for ( Property property : properties ) {
            wrapperList.add( new PropertyWrapperRow( property ) );
        }
        return wrapperList;
    }

    private List<Property> unWrappPropertiesList( List<PropertyRow> propertyRows ) {
        List<Property> properties = new ArrayList<Property>(  );
        if ( propertyRows == null ) return null;
        for ( PropertyRow propertyRow : propertyRows ) {
            properties.add( new Property( propertyRow.getName(), propertyRow.getValue() ) );
        }
        return properties;
    }

    public static class PropertyWrapperRow implements PropertyRow {

        private Property property = new Property(  );

        public PropertyWrapperRow( Property property ) {
            if ( property != null ) {
                this.property = property;
            }
        }

        @Override public String getName() {
            return property.getName();
        }

        @Override public void setName( String name ) {
            property.setName( name );
        }

        @Override public String getValue() {
            return property.getValue();
        }

        @Override public void setValue( String value ) {
            property.setValue( value );
        }
    }

}

