/*
 * ChooseMirrorDialog.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.common.mirrors;

import java.util.ArrayList;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.FocusHelper;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.SimplePanelWithProgress;
import org.rstudio.core.client.widget.images.ProgressImages;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.mirrors.model.CRANMirror;
import org.rstudio.studio.client.server.ServerDataSource;
import org.rstudio.studio.client.server.ServerError;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class ChooseMirrorDialog extends ModalDialog<CRANMirror>
{
   public interface Source 
                    extends ServerDataSource<JsArray<CRANMirror>>
   {
      String getLabel(CRANMirror mirror);
      String getURL(CRANMirror mirror);
   }
   
   public ChooseMirrorDialog(GlobalDisplay globalDisplay,
                             Source mirrorSource,
                             OperationWithInput<CRANMirror> inputOperation)
   {
      super("Retrieving list of CRAN mirrors...", inputOperation);
      globalDisplay_ = globalDisplay;
      mirrorSource_ = mirrorSource;
      enableOkButton(false);
   }

   @Override
   protected CRANMirror collectInput()
   {
      if (!StringUtil.isNullOrEmpty(customTextBox_.getText()))
      {
         CRANMirror cranMirror = CRANMirror.empty();
         cranMirror.setURL(customTextBox_.getText());

         cranMirror.setHost("Custom");
         cranMirror.setName("Custom");

         return cranMirror;
      }
      else if (listBox_ != null && listBox_.getSelectedIndex() >= 0)
      {
         return mirrors_.get(listBox_.getSelectedIndex());
      }
      else
      {
         return null;
      }
   }

   @Override
   protected boolean validate(CRANMirror input)
   {
      if (input == null)
      {
         globalDisplay_.showErrorMessage("Error", 
                                         "Please select a CRAN Mirror");
         return false;
      }
      else
      {
         return true;
      }
   }

   @Override
   protected Widget createMainWidget()
   {
      VerticalPanel root = new VerticalPanel();

      Label customLabel = new Label("Custom:");
      root.add(customLabel);

      customTextBox_ = new TextBox();
      customTextBox_.setStylePrimaryName(RESOURCES.styles().customRepo());
      root.add(customTextBox_);

      Label mirrorsLabel = new Label("CRAN Mirrors:");
      mirrorsLabel.getElement().getStyle().setMarginTop(8, Unit.PX);
      root.add(mirrorsLabel);

      // create progress container
      final SimplePanelWithProgress panel = new SimplePanelWithProgress(
                                          ProgressImages.createLargeGray());
      root.add(panel);

      panel.setStylePrimaryName(RESOURCES.styles().mainWidget());
         
      // show progress (with delay)
      panel.showProgress(200);
      
      // query data source for packages
      mirrorSource_.requestData(new SimpleRequestCallback<JsArray<CRANMirror>>() {

         @Override 
         public void onResponseReceived(JsArray<CRANMirror> mirrors)
         {   
            // keep internal list of mirrors 
            boolean haveInsecureMirror = false;
            mirrors_ = new ArrayList<CRANMirror>(mirrors.length());
            
            // create list box and select default item
            listBox_ = new ListBox();
            listBox_.setMultipleSelect(false);
            listBox_.setVisibleItemCount(18); // all
            listBox_.setWidth("100%");
            if (mirrors.length() > 0)
            {
               for(int i=0; i<mirrors.length(); i++)
               {
                  CRANMirror mirror = mirrors.get(i);
                  if (mirrorSource_.getLabel(mirror).startsWith("0-Cloud"))
                     continue;
                  mirrors_.add(mirror);
                  String item = mirrorSource_.getLabel(mirror);
                  String value = mirrorSource_.getURL(mirror);
                  if (!value.startsWith("https"))
                     haveInsecureMirror = true;

                  listBox_.addItem(item, value);
               }
               
               listBox_.setSelectedIndex(0);
               enableOkButton(true);
            }
            
            // set it into the panel
            panel.setWidget(listBox_);
            
            // set caption
            setText("Choose Primary Repo");
          
            // update ok button on changed
            listBox_.addDoubleClickHandler(new DoubleClickHandler() {
               @Override
               public void onDoubleClick(DoubleClickEvent event)
               {
                  clickOkButton();              
               }
            });
            
            
            // if the list box is larger than the space we initially allocated
            // then increase the panel height
            final int kDefaultPanelHeight = 265;
            if (listBox_.getOffsetHeight() > kDefaultPanelHeight)
               panel.setHeight(listBox_.getOffsetHeight() + "px");
            
            // set focus   
            FocusHelper.setFocusDeferred(listBox_);
         }
         
         @Override
         public void onError(ServerError error)
         {
            closeDialog();
            super.onError(error);
         }
      });
      
      return root;
   }
   
   static interface Styles extends CssResource
   {
      String mainWidget();
      String customRepo();
   }
  
   static interface Resources extends ClientBundle
   {
      @Source("ChooseMirrorDialog.css")
      Styles styles();
   }
   
   static Resources RESOURCES = (Resources)GWT.create(Resources.class) ;
   public static void ensureStylesInjected()
   {
      RESOURCES.styles().ensureInjected();
   }
   
   private final GlobalDisplay globalDisplay_ ;
   private final Source mirrorSource_;
   private ArrayList<CRANMirror> mirrors_ = null;
   private ListBox listBox_ = null;
   private TextBox customTextBox_ = null;

}
