/*
 * Copyright 2015 lprimak.
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
package com.flowlogix.security.cdi;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.SessionListener;
import org.apache.shiro.session.SessionListenerAdapter;

/**
 * Entry point for Shiro Session scope CDI extension
 * 
 * @author lprimak
 */
public class ShiroSessionScopeExtension implements Extension, Serializable
{
    /**
     * intercept session destroy session listeners and destroy the beans
     * 
     * @param sessionListeners 
     */
    public void addDestroyHandlers(Collection<SessionListener> sessionListeners)
    {
        sessionListeners.add(new SessionListenerAdapter()
        {
            @Override
            public void onStop(Session session)
            {
                contexts.forEach(ctx -> ctx.onDestroy(session));
            }

            @Override
            public void onExpiration(Session session)
            {
                onStop(session);
            }
        });
    }

    
    private void addScope(@Observes final BeforeBeanDiscovery event)
    {
        contexts.forEach(ctx -> event.addScope(ctx.getScope(), true, true));
    }

    
    private void registerContext(@Observes final AfterBeanDiscovery event)
    {
        contexts.forEach(ctx -> event.addContext(ctx));
    }
    
    
    private final List<ShiroScopeContext> contexts = ImmutableList.of(
            new ShiroScopeContext(ShiroSessionScoped.class), new ShiroScopeContext(ShiroViewScoped.class));
    private static final long serialVersionUID = 1L;
}
