/*
 * Copyright 2014 lprimak.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flowlogix.ui;

import java.io.IOException;
import java.util.Iterator;
import javax.faces.FacesException;
import javax.faces.application.ViewExpiredException;
import javax.faces.context.ExceptionHandler;
import javax.faces.context.ExceptionHandlerFactory;
import javax.faces.context.ExceptionHandlerWrapper;
import javax.faces.context.PartialResponseWriter;
import javax.faces.event.ExceptionQueuedEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.omnifaces.util.Exceptions;
import org.omnifaces.util.Faces;

/**
 * establishes a session for ajax exceptions
 * 
 * @author lprimak
 */
@RequiredArgsConstructor
public class ViewExpiredExceptionHandlerFactory  extends ExceptionHandlerFactory
{
    @Override
    public ExceptionHandler getExceptionHandler()
    {
        return new Handler(wrapped.getExceptionHandler());
    }
    

    @RequiredArgsConstructor
    private static class Handler extends ExceptionHandlerWrapper
    {
        @Override
        @SneakyThrows(IOException.class)
        public void handle() throws FacesException
        {
            Iterator<ExceptionQueuedEvent> it = getUnhandledExceptionQueuedEvents().iterator();
            while(it.hasNext())
            {
                ExceptionQueuedEvent evt = it.next();
                Throwable ex = Exceptions.unwrap(evt.getContext().getException());
                if (ex instanceof ViewExpiredException)
                {               
                    if (Faces.isAjaxRequest())
                    {
                        Faces.responseReset();
                        
                        Faces.getResponse().setHeader("Cache-Control", "no-cache");
                        Faces.getResponse().setCharacterEncoding(Faces.getResponseCharacterEncoding());
                        Faces.getResponse().setContentType("text/xml");

                        PartialResponseWriter writer = Faces.getContext().getPartialViewContext().getPartialResponseWriter();
                        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
                        writer.startElement("partial-response", null);
                        writer.startElement("redirect", null);
                        writer.writeAttribute("url", Faces.getRequestURIWithQueryString(), null);
                        writer.endElement("redirect");
                        writer.endElement("partial-response");
                        
                        Faces.responseComplete();
                    } 
                    else
                    {
                        Faces.redirect(Faces.getRequestURIWithQueryString());
                    }
                    it.remove();
                }
            }
            getWrapped().handle();
        }
        
        
        private @Getter final ExceptionHandler wrapped;
    }    
        
    
    private @Getter final ExceptionHandlerFactory wrapped;
}
