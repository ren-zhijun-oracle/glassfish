/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package test.com.sun.jaspic.config;

import java.util.ArrayList;
import java.util.List;
import com.sun.jaspic.config.factory.EntryInfo;
import com.sun.jaspic.config.factory.AuthConfFileFactory;
import com.sun.jaspic.config.factory.BaseAuthConfigFactory;
import com.sun.jaspic.config.factory.RegStoreFileParser;
import java.security.Security;
import org.junit.After;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigFactory.RegistrationContext;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.ClientAuthContext;
import javax.security.auth.message.config.RegistrationListener;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Ron Monzillo
 */
public class FactoryTest {

    /** switch definition of default test factory to test native factory
     * will also need to configure proper dependencies
     */
    // private static final String DEFAULT_TEST_FACTORY_CLASS_NAME = "com.sun.enterprise.security.jmac.config.SaveAuthConfigFactory";
    private static final String DEFAULT_TEST_FACTORY_CLASS_NAME = AuthConfFileFactory.class.getName();
    private static String testFactoryClassName = DEFAULT_TEST_FACTORY_CLASS_NAME;
    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY =
            "authconfigprovider.factory";
    public static final String USER_DIR_PROPERTY = "user.dir";
    static final String THREAD_COUNT_KEY = "test.thread.count";
    static final String MAX_JOIN_SECONDS_KEY = "test.max.join.seconds";
    static final String TEST_FACTORY_CLASS_NAME_KEY = "test.factory.class.name";
    static final int DEFAULT_THREAD_COUNT = 32;
    static final int DEFAULT_MAX_JOIN_SECONDS = 30;
    static final Logger logger = Logger.getLogger(FactoryTest.class.getName());
    private static String defaultFactoryClassName;
    private static AuthConfigFactory testFactory;
    static HashMap<String, String> options = new HashMap<String, String>();
    static _Thread[] threads;
    static int consumerCount;
    static int activeConsumers;
    static Random random = new Random();
    static final String[] layers = new String[4];
    static final String[] contexts = new String[16];

    static {
        layers[0] = null;
        for (int i = 1; i < layers.length; i++) {
            layers[i] = "layer" + Integer.toString(i);
        }
        contexts[0] = null;
        for (int i = 1; i < contexts.length; i++) {
            contexts[i] = "context" + Integer.toString(i);
        }
    }
    static AuthConfigProvider[] providers = new AuthConfigProvider[4];

    public FactoryTest() {
    }

    public static String getStringOption(String key, String defaultValue) {
        String s = options == null ? null : (String) options.get(key);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    public static int getIntOption(String key, int defaultValue) {
        String s = options == null ? null : (String) options.get(key);
        if (s == null) {
            return defaultValue;
        }
        return Integer.valueOf(s).intValue();
    }

    public static boolean getbooleanOption(String key, boolean defaultValue) {
        String s = options == null ? null : (String) options.get(key);
        if (s == null) {
            return defaultValue;
        }
        return Boolean.valueOf(s).booleanValue();
    }

    public static String[] splitStringOption(String s) {
        if (s == null) {
            return new String[0];
        }
        return s.split(",|:| |;");
    }

    static Map<String, String> getProviderProperties() {
        return new HashMap<String, String>();
    }

    static AuthConfigFactory loadFactory(final String className) {
        try {
            return (AuthConfigFactory) java.security.AccessController.doPrivileged(new java.security.PrivilegedExceptionAction() {

                @Override
                public Object run()
                        throws ClassNotFoundException,
                        InstantiationException,
                        IllegalAccessException {
                    ClassLoader loader =
                            Thread.currentThread().
                            getContextClassLoader();

                    Class clazz = Class.forName(className, true, loader);
                    return clazz.newInstance();
                }
            });
        } catch (java.security.PrivilegedActionException pae) {
            throw new SecurityException(pae.getException());
        }
    }

    public static void main(String[] args) {

        for (String s : args) {
            StringTokenizer tokenizer = new StringTokenizer(s, "=");
            if (tokenizer.countTokens() == 2) {
                String key = tokenizer.nextToken();
                String value = tokenizer.nextToken();
                System.out.println("key: " + key + " value: " + value);
                options.put(key, value);
            }
        }

        testFactoryClassName = getStringOption(TEST_FACTORY_CLASS_NAME_KEY,
                DEFAULT_TEST_FACTORY_CLASS_NAME);

        new FactoryTest().beforeTest();
        new FactoryTest().testSetFactory();
        new FactoryTest().afterTest();

        new FactoryTest().beforeTest();
        new FactoryTest().testOverrideForDefaultEntries();
        new FactoryTest().afterTest();

        new FactoryTest().beforeTest();
        new FactoryTest().testRemoveRegistration();
        new FactoryTest().afterTest();

        new FactoryTest().beforeTest();
        new FactoryTest().testListeners();
        new FactoryTest().afterTest();

        new FactoryTest().beforeTest();
        new FactoryTest().stressFactory(
                getIntOption(THREAD_COUNT_KEY, DEFAULT_THREAD_COUNT),
                getIntOption(MAX_JOIN_SECONDS_KEY, DEFAULT_MAX_JOIN_SECONDS));
        new FactoryTest().afterTest();

    }

    @Before
    public void beforeTest() {
        defaultFactoryClassName = Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
        logger.log(Level.INFO, "\n\tSecurity Manager is {0}\n\t{1} is {2}\n\t{3} is {4}\n\t{5} is {6}\n",
                new Object[]{
                    (System.getSecurityManager() == null ? "OFF" : "ON"),
                    DEFAULT_FACTORY_SECURITY_PROPERTY, defaultFactoryClassName,
                    "Test Factory Class Name", testFactoryClassName,
                    USER_DIR_PROPERTY, System.getProperty(USER_DIR_PROPERTY)});
        testFactory = loadFactory(testFactoryClassName);
        AuthConfigFactory.setFactory(testFactory);
    }

    @After
    public void afterTest() {
        AuthConfigFactory.setFactory(null);
    }

    @Test
    public void testSetFactory() {
        logger.info("BEGIN Set FACTORY TEST");
        AuthConfigFactory.setFactory(null);
        assertTrue(defaultFactoryClassName == null
                ? AuthConfigFactory.getFactory() == null
                : defaultFactoryClassName.equals(AuthConfigFactory.getFactory().getClass().getName()));
        if (defaultFactoryClassName != null) {
            Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, testFactoryClassName);
            AuthConfigFactory.setFactory(null);
            assertTrue(testFactoryClassName.equals(AuthConfigFactory.getFactory().getClass().getName()));
            Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, defaultFactoryClassName);
            AuthConfigFactory.setFactory(null);
            assertTrue(defaultFactoryClassName.equals(AuthConfigFactory.getFactory().getClass().getName()));
        }
        AuthConfigFactory.setFactory(testFactory);
        assertTrue(testFactoryClassName.equals(AuthConfigFactory.getFactory().getClass().getName()));
    }

    @Test
    public void testOverrideForDefaultEntries() {
        logger.info("BEGIN overrideGetDefaultEntries TEST");
        AuthConfigFactory f = new _ExtendsBaseAuthConfigFactory();
        f = new _Extends_ExtendsAuthConfigFactory();
    }

    static class _ExtendsBaseAuthConfigFactory extends BaseAuthConfigFactory {

        // regStore MUST hide regStore of bade class
        private static RegStoreFileParser regStore = null;

        /**
         * To specialize the defaultEntries passed to the RegStoreFileParser,
         * construct EntryInfo objects within this constructor.
         * THE EentyInfo OBJECTS MUST ONLY BE CONSTRCTED USING THE FOLLOWING
         * CONSTRUCTOR: EntryInfo(String className, Map<String, String> properties)
         * NO Entries are passed by this test, because to do so, the parent
         * test class would need to import EntryInfo (which it can't).
         */
        public _ExtendsBaseAuthConfigFactory() {
            rLock.lock();
            try {
                if (regStore != null) {
                    return;
                }
            } finally {
                rLock.unlock();
            }
            String userDir = System.getProperty("user.dir");
            wLock.lock();
            try {
                if (regStore == null) {
                    EntryInfo e = new EntryInfo(_AuthConfigProvider.class.getName(),null);
                    List<EntryInfo> defaultEntries = new ArrayList<EntryInfo>();
                    defaultEntries.add(e);
                    regStore = new RegStoreFileParser(userDir,
                            BaseAuthConfigFactory.CONF_FILE_NAME,defaultEntries);
                    _loadFactory();
                }
            } finally {
                wLock.unlock();
            }
            RegStoreFileParser rS = getRegStore();
            assertTrue(rS == _ExtendsBaseAuthConfigFactory.regStore);
        }

        @Override
        protected RegStoreFileParser getRegStore() {
            rLock.lock();
            try {
                return regStore;
            } finally {
                rLock.unlock();
            }
        }
    }

    static class _Extends_ExtendsAuthConfigFactory extends _ExtendsBaseAuthConfigFactory {

        // regStore MUST hide regStore of base class
        private static RegStoreFileParser regStore = null;

        /**
         * To specialize the defaultEntries passed to the RegStoreFileParser,
         * construct EntryInfo objects within this constructor.
         * THE EentyInfo OBJECTS MUST ONLY BE CONSTRCTED USING THE FOLLOWING
         * CONSTRUCTOR: EntryInfo(String className, Map<String, String> properties)
         * NO Entries are passed by this test, because to do so, the parent
         * test class would need to import EntryInfo (which it can't).
         */
        public _Extends_ExtendsAuthConfigFactory() {
            rLock.lock();
            try {
                if (regStore != null) {
                    return;
                }
            } finally {
                rLock.unlock();
            }
            String userDir = System.getProperty("user.dir");
            wLock.lock();
            try {
                if (regStore == null) {
                    EntryInfo e = new EntryInfo(_AuthConfigProvider.class.getName(),null);
                    List<EntryInfo> defaultEntries = new ArrayList<EntryInfo>();
                    defaultEntries.add(e);
                    regStore = new RegStoreFileParser(userDir,
                            BaseAuthConfigFactory.CONF_FILE_NAME,defaultEntries);
                    _loadFactory();
                }
            } finally {
                wLock.unlock();
            }
            RegStoreFileParser rS = getRegStore();
            assertTrue(rS == _Extends_ExtendsAuthConfigFactory.regStore);
        }

        @Override
        protected RegStoreFileParser getRegStore() {
            rLock.lock();
            try {
                return regStore;
            } finally {
                rLock.unlock();
            }
        }
    }
    
    @Test
    public void testRemoveRegistration() {
        logger.info("BEGIN Remove Registration TEST");
        final AuthConfigFactory f = AuthConfigFactory.getFactory();
        f.refresh();
        // does self registration
        AuthConfigProvider p = new _AuthConfigProvider(new HashMap(), f);
        RegistrationContext rc;
        String[] rids = f.getRegistrationIDs(p);
        boolean removed;
        assertTrue("provider did not self register", rids != null && rids.length > 0);
        for (String i : rids) {
            rc = f.getRegistrationContext(i);
            removed = f.removeRegistration(i);
            assertTrue("expected true from removeRegistration - rid: " + i,
                    rc != null && removed);
        }
        for (String i : rids) {
            rc = f.getRegistrationContext(i);
            removed = f.removeRegistration(i);
            assertTrue("expected false from removeRegistration - rid: " + i,
                    rc == null && !removed);
        }

        //testing registration and removal of null provider;
        String rid = f.registerConfigProvider(null, null, null, "null registration");
        rc = f.getRegistrationContext(rid);
        removed = f.removeRegistration(rid);
        assertTrue("testing null provider - expected true from removeRegistration - rid: " + rid,
                rc != null && removed);
        //testing for interferece with null provider
        rc = f.getRegistrationContext(rid);
        removed = f.removeRegistration(rid);
        assertTrue("testing null provider - expected false from removeRegistration - rid: " + rid,
                rc == null && !removed);
        rid = f.registerConfigProvider(null, null, null, "null registration");
        //temporary to force call to decomposeRegId in getEffectedListeners
        p = f.getConfigProvider(null, null, new _Listener(null, null, false));
        rc = f.getRegistrationContext(rid);
        assertTrue("testing null provider - getRegistrationContext - rid: " + rid,
                rid != null);
        String badRid = "someInvalidId";
        rc = f.getRegistrationContext(badRid);
        removed = f.removeRegistration(badRid);
        assertTrue("expected false from removeRegistration - rid: " + badRid,
                rc == null && !removed);
        rc = f.getRegistrationContext(rid);
        removed = f.removeRegistration(rid);
        assertTrue("testing null provider - expected true from removeRegistration - rid: " + rid,
                rc != null && removed);
    }

    @Test
    public void testListeners() {
        logger.info("BEGIN Listener TEST");
        final AuthConfigFactory f = AuthConfigFactory.getFactory();
        final AuthConfigProvider p = new _AuthConfigProvider(new HashMap(), null);

        String layer[] = {null, "11", "l2"};
        String context[] = {null, "c1", "c2"};
        String rid[] = new String[(layer.length - 1) * (context.length - 1)];
        String ridLayer[] = new String[rid.length];
        String ridContext[] = new String[rid.length];

        int z = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                ridLayer[z] = layer[i];
                ridContext[z] = context[j];
                rid[z] = f.registerConfigProvider(p, ridLayer[z], ridContext[z],
                        ridLayer[z] + ridContext[z]);
                z++;
            }
        }

        _Listener listener[] = new _Listener[rid.length];

        z = 0;
        for (int i = 1; i < layer.length; i++) {
            for (int j = 1; j < context.length; j++) {
                listener[z] = new _Listener(layer[i], context[j], true);
                f.getConfigProvider(layer[i], context[j], listener[z]);
                z++;
            }
        }

        for (int i = 0; i < rid.length; i++) {
            f.removeRegistration(rid[i]);
        }


        f.detachListener(listener[0], null, null);
        f.detachListener(listener[1], listener[1].getLayer(), null);
        f.detachListener(listener[2], null, listener[2].getAppContext());
        f.detachListener(listener[3], listener[3].getLayer(), listener[3].getAppContext());

        //should not find any left to detach
        for (int i = 0; i < listener.length; i++) {
            f.detachListener(listener[i], listener[i].getLayer(), listener[i].getAppContext());
        }

        for (int i = 0; i < rid.length; i++) {
            rid[i] = f.registerConfigProvider(p, ridLayer[i], ridContext[i], ridLayer[i] + ridContext[i]);
        }

        z = 0;
        for (int i = 1; i < layer.length; i++) {
            for (int j = 1; j < context.length; j++) {
                listener[z] = new _Listener(layer[i], context[j], false);
                f.getConfigProvider(layer[i], context[j], listener[z]);
                z++;
            }
        }
        for (int i = 0; i < rid.length; i++) {
            for (int j = 0; j < listener.length; j++) {
                if (listener[j].notified) {
                    assertTrue("Test Setup Failure - listener could not be registered",
                            listener[j].register());
                }
            }
            f.removeRegistration(rid[i]);

            for (int j = 0; j < listener.length; j++) {
                listener[j].check(ridLayer[i], ridContext[i]);
            }
        }

        //repeat with null provider registrations
        for (int i = 0; i < rid.length; i++) {
            rid[i] = f.registerConfigProvider(null, ridLayer[i], ridContext[i], ridLayer[i] + ridContext[i]);
        }

        z = 0;
        for (int i = 1; i < layer.length; i++) {
            for (int j = 1; j < context.length; j++) {
                listener[z] = new _Listener(layer[i], context[j], false);
                f.getConfigProvider(layer[i], context[j], listener[z]);
                z++;
            }
        }
        for (int i = 0; i < rid.length; i++) {
            for (int j = 0; j < listener.length; j++) {
                if (listener[j].notified) {
                    assertTrue("Test Setup Failure - listener could not be registered",
                            listener[j].register());
                }
            }
            f.removeRegistration(rid[i]);

            for (int j = 0; j < listener.length; j++) {
                listener[j].check(ridLayer[i], ridContext[i]);
            }
        }
    }

    static class _Listener implements RegistrationListener {

        String layer;
        String appContext;
        boolean reRegister;
        boolean notified;

        _Listener(String layer, String appContext, boolean reRegister) {
            this.layer = layer;
            this.appContext = appContext;
            this.reRegister = reRegister;
            this.notified = false;
        }

        String getLayer() {
            return layer;
        }

        String getAppContext() {
            return appContext;
        }

        synchronized boolean register() {
            boolean rvalue = false;
            if (notified) {
                notified = false;
                rvalue = true;
                AuthConfigFactory.getFactory().getConfigProvider(layer, appContext, this);
            }
            return rvalue;
        }

        synchronized boolean notified() {
            return notified;
        }

        void check(String l, String c) {
            boolean shouldHaveBeenNotified = false;
            if ((l == null || layer.equals(l)) && (c == null || appContext.equals(c))) {
                shouldHaveBeenNotified = true;
            }
            if (shouldHaveBeenNotified) {
                String msg = "listener at layer,context: " + layer + "," + appContext + " should have been notified at: "
                        + l + "," + c;
                assertTrue(msg, notified());
            } else {
                String msg = "listener at layer,context: " + layer + "," + appContext + " should NOT have been notified at: "
                        + l + "," + c;
                assertFalse(msg, notified());
            }
        }

        @Override
        public void notify(String l, String c) {
            synchronized (this) {
                notified = true;
            }
            boolean validNotification = (layer == l || layer.equals(l))
                    && (appContext == c || appContext.equals(c));
            String msg = "listener notified at wrong layer: " + l + " or context: " + c;
            assertTrue(msg, validNotification);
            if (validNotification && reRegister) {
                register();
            }
        }
    }

    @Test
    public void stressFactory() {
        stressFactory(DEFAULT_THREAD_COUNT, DEFAULT_MAX_JOIN_SECONDS);
    }

    public void stressFactory(int threadCount, int maxJoinSeconds) {

        logger.info("BEGIN stress FACTORY TEST");
        AuthConfigFactory f = AuthConfigFactory.getFactory();
        f.refresh();
        providers[0] = null;

        for (int i = 1; i < providers.length; i++) {
            providers[i] = new _AuthConfigProvider(getProviderProperties(), null);
        }
        threads = new _Thread[threadCount];
        synchronized (_Thread.class) {
            activeConsumers = threads.length;
            consumerCount = threads.length;
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new _Thread();
        }
        logger.log(Level.INFO, "STARTING {0} THREADS", threads.length);
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (_Thread t : threads) {
            try {
                t.join(maxJoinSeconds * 1000);
                if (t.isAlive()) {
                    fail("interrupting thread: " + t.getId()
                            + " because it did not terminate in: " + maxJoinSeconds + " seconds");
                    t.interrupt();
                }
            } catch (InterruptedException ex) {
                fail("thread: " + t.getId() + " caught exception:" + ex.toString());
            } finally {
                logger.log(Level.FINE, "thread: {0} completed: {1}",
                        new Object[]{t.getId(), t.runAsConsumer() ? "comsumer" : "producer"});
            }
        }

        synchronized (_Thread.class) {
            logger.log(Level.INFO, "ALL THREADS JOINED - producers: {0} consumers: {1}",
                    new Object[]{threads.length - consumerCount, consumerCount});
        }

        String[] rids = f.getRegistrationIDs(null);
        for (String i : rids) {
            RegistrationContext rc = f.getRegistrationContext(i);
            f.removeRegistration(i);
        }
        logger.info("ALL REGISTRATIONS REMOVED");

        f.refresh();
    }

    static class _Thread extends Thread implements RegistrationListener {

        boolean runAsConsumer = false;
        boolean stop;

        @Override
        public void run() {
            synchronized (_Thread.class) {
                if (consumerCount == threads.length) {
                    runAsConsumer = false;
                } else {
                    runAsConsumer = (random.nextInt(10) != 1);
                }
            }
            AuthConfigFactory f = AuthConfigFactory.getFactory();
            if (runAsConsumer) {
                doConsumer(f, layers[random.nextInt(layers.length)], contexts[random.nextInt(contexts.length)]);
            } else {
                synchronized (_Thread.class) {
                    consumerCount--;
                    activeConsumers--;
                    logger.log(Level.FINE, "creating producer, remaining consumers: {0}", consumerCount);
                }
                while (true) {
                    synchronized (_Thread.class) {
                        if (activeConsumers == 0) {
                            return;
                        }
                    }
                    switch (random.nextInt(5)) {
                        case 0:
                            if (random.nextInt(25) == 1) {
                                try {
                                    f.refresh();
                                } catch (Exception e) {
                                    fail("producer thread(refresh): " + getId() + " caught exception:" + e.toString());
                                }
                            }
                            break;
                        case 1:
                            if (random.nextInt(1000) == 1) {
                                try {
                                    f = AuthConfigFactory.getFactory();
                                    AuthConfigFactory.setFactory(f);
                                } catch (Exception e) {
                                    fail("producer thread (get/setFactory): " + getId() + " caught exception:" + e.toString());
                                }
                            }
                            break;
                        case 2:
                            try {
                                f.registerConfigProvider(
                                        _AuthConfigProvider.class.getName(),
                                        getProviderProperties(),
                                        layers[random.nextInt(layers.length)],
                                        contexts[random.nextInt(contexts.length)],
                                        "persistent registration");
                            } catch (Exception e) {
                                fail("producer thread (register persistent): " + getId() + " caught exception:" + e.toString());
                            }
                            break;
                        case 3:
                            try {
                                f.registerConfigProvider(
                                        providers[random.nextInt(providers.length)],
                                        layers[random.nextInt(layers.length)],
                                        contexts[random.nextInt(contexts.length)],
                                        "transient registration");
                            } catch (Exception e) {
                                fail("producer thread (register transient): " + getId() + " caught exception:" + e.toString());
                            }
                            break;
                        case 4:
                            try {
                                String[] rids = f.getRegistrationIDs(
                                        providers[random.nextInt(providers.length)]);
                                int length = rids.length;
                                boolean removeNext = true;
                                for (String rid : rids) {
                                    RegistrationContext rc = f.getRegistrationContext(rid);
                                    if (rc == null) {
                                        removeNext = true;
                                    } else if (removeNext) {
                                        f.removeRegistration(rid);
                                        removeNext = false;
                                    } else {
                                        removeNext = true;
                                    }
                                }
                            } catch (Exception e) {
                                fail("producer thread (removeRegistration): " + getId() + " caught exception:" + e.toString());
                            }
                            break;

                    }
                }
            }
        }

        public boolean runAsConsumer() {
            return runAsConsumer;
        }

        public void doConsumer(AuthConfigFactory f, String layer, String context) {

            synchronized (_Thread.class) {
                logger.log(Level.FINE, "creating consumer");
                this.stop = false;
            }
            try {
                f.getConfigProvider(layer, context, this);
                while (true) {
                    sleep(10);
                    synchronized (_Thread.class) {
                        if (this.stop) {
                            break;
                        }
                    }
                }
                f.detachListener(this, null, null);
            } catch (Exception e) {
                fail("consumer thread: " + getId() + " caught exception:" + e.toString());
            } finally {
                synchronized (_Thread.class) {
                    activeConsumers--;
                    logger.log(Level.INFO, "consumer thread: {0} stopping - remaining: {1}",
                            new Object[]{getId(), activeConsumers});
                }
            }
        }

        public void notify(String layer, String context) {
            if (random.nextInt(100) == 1) {
                synchronized (_Thread.class) {
                    this.stop = true;
                }
            } else {
                AuthConfigFactory.getFactory().getConfigProvider(layer, context, this);
            }
        }
    }

    public static class _AuthConfigProvider implements AuthConfigProvider {

        public _AuthConfigProvider(Map<String, String> properties, AuthConfigFactory f) {
            if (f != null) {
                f.registerConfigProvider(this,
                        layers[random.nextInt(layers.length)],
                        contexts[random.nextInt(contexts.length)],
                        "self registration");
            }
        }

        public ClientAuthConfig getClientAuthConfig(final String layer,
                final String appCtxt, CallbackHandler ch) throws AuthException {

            return new ClientAuthConfig() {

                public ClientAuthContext getAuthContext(String string, Subject sbjct, Map map) throws AuthException {
                    throw new UnsupportedOperationException();
                }

                public String getMessageLayer() {
                    return layer;
                }

                public String getAppContext() {
                    return appCtxt;
                }

                public String getAuthContextID(MessageInfo mi) {
                    throw new UnsupportedOperationException();
                }

                public void refresh() {
                }

                public boolean isProtected() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public ServerAuthConfig getServerAuthConfig(final String layer,
                final String appCtxt, CallbackHandler ch) throws AuthException {

            return new ServerAuthConfig() {

                public ServerAuthContext getAuthContext(String string, Subject sbjct, Map map) throws AuthException {
                    throw new UnsupportedOperationException();
                }

                public String getMessageLayer() {
                    return layer;
                }

                public String getAppContext() {
                    return appCtxt;
                }

                public String getAuthContextID(MessageInfo mi) {
                    throw new UnsupportedOperationException();
                }

                public void refresh() {
                }

                public boolean isProtected() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public void refresh() {
        }
    }

    

}
