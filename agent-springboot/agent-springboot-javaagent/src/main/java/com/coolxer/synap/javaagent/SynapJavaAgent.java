package com.coolxer.synap.javaagent;

import com.coolxer.synap.core.AgentConfiguration;
import com.coolxer.synap.core.AgentRuntime;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static net.bytebuddy.matcher.ElementMatchers.*;

public final class SynapJavaAgent {
    private SynapJavaAgent() {}
    public static void premain(String arguments, Instrumentation instrumentation) { install(arguments, instrumentation); }
    public static void agentmain(String arguments, Instrumentation instrumentation) { install(arguments, instrumentation); }

    private static void install(String arguments, Instrumentation instrumentation) {
        try {
            appendBridgeToBootstrap(instrumentation);
            System.setProperty("synap.javaagent.active", "true");
            Path injection = Files.createTempDirectory("synap-agent-injection-"); injection.toFile().deleteOnExit();
            AgentBuilder builder = new AgentBuilder.Default()
                    .disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(new AgentBuilder.InjectionStrategy.UsingInstrumentation(instrumentation, injection.toFile()))
                    .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("com.coolxer.synap.shaded.bytebuddy."))
                            .or(nameStartsWith("com.coolxer.synap."))
                            .or(isSynthetic()));
            if (Boolean.getBoolean("synap.agent.debug")) builder = builder.with(AgentBuilder.Listener.StreamWriting.toSystemError().withTransformationsOnly());
            builder = advice(builder, named("java.lang.ProcessBuilder"), named("start"), SinkAdvices.Command.class);
            builder = advice(builder, named("java.lang.Runtime"), nameStartsWith("exec"), SinkAdvices.Command.class);
            builder = advice(builder, named("java.nio.file.Files"), nameStartsWith("read").or(nameStartsWith("write")).or(nameStartsWith("newInputStream"))
                    .or(nameStartsWith("newOutputStream")).or(nameStartsWith("delete")).or(nameStartsWith("move")).or(nameStartsWith("copy")), SinkAdvices.File.class);
            builder = advice(builder, named("java.io.FileInputStream").or(named("java.io.FileOutputStream")).or(named("java.io.RandomAccessFile")), isConstructor(), SinkAdvices.FileConstructor.class);
            builder = advice(builder, named("javax.naming.InitialContext"), named("lookup"), SinkAdvices.Jndi.class);
            builder = advice(builder, named("java.io.ObjectInputStream"), named("readObject"), SinkAdvices.Deserialization.class);
            builder = advice(builder, hasSuperType(named("java.sql.Statement")).and(not(isInterface())), nameStartsWith("execute"), SinkAdvices.Sql.class);
            builder = advice(builder, hasSuperType(named("java.net.http.HttpClient")).and(not(isInterface())), named("send").or(named("sendAsync")), SinkAdvices.Http.class);
            builder = advice(builder, named("org.springframework.web.client.RestTemplate"), named("doExecute"), SinkAdvices.Http.class);
            builder = advice(builder, nameStartsWith("org.apache.http.impl.client.").or(nameStartsWith("org.apache.hc.client5.http.impl.classic.")),
                    named("execute").or(named("doExecute")), SinkAdvices.Http.class);
            builder = advice(builder, named("okhttp3.RealCall").or(named("okhttp3.internal.connection.RealCall")),
                    named("execute").or(named("enqueue")), SinkAdvices.Http.class);
            builder = advice(builder, nameStartsWith("org.springframework.web.reactive.function.client.DefaultWebClient"),
                    named("exchange").or(named("exchangeToMono")).or(named("exchangeToFlux")).or(named("retrieve")), SinkAdvices.Http.class);
            builder = advice(builder, named("org.springframework.expression.spel.standard.SpelExpression"), named("getValue"), SinkAdvices.Spel.class);
            builder.installOn(instrumentation);
            AgentRuntime.start(AgentConfiguration.fromEnvironment(), "javaagent-only");
        } catch (Throwable error) {
            System.err.println("[synap-agent] javaagent installation failed: " + error.getClass().getSimpleName());
            if (Boolean.getBoolean("synap.agent.debug")) error.printStackTrace(System.err);
        }
    }

    private static AgentBuilder advice(AgentBuilder builder, net.bytebuddy.matcher.ElementMatcher.Junction<?> type,
                                       net.bytebuddy.matcher.ElementMatcher.Junction<?> method, Class<?> advice) {
        @SuppressWarnings("unchecked")
        net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.type.TypeDescription> typeMatcher =
                (net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.type.TypeDescription>) type;
        @SuppressWarnings("unchecked")
        net.bytebuddy.matcher.ElementMatcher<? super net.bytebuddy.description.method.MethodDescription> methodMatcher =
                (net.bytebuddy.matcher.ElementMatcher<? super net.bytebuddy.description.method.MethodDescription>) method;
        return builder.type(typeMatcher).transform((dynamicBuilder, typeDescription, classLoader, module, protectionDomain) ->
                dynamicBuilder.visit(Advice.to(advice).on(methodMatcher)));
    }

    private static void appendBridgeToBootstrap(Instrumentation instrumentation) throws Exception {
        String[] resources = {"com/coolxer/synap/bridge/AgentBridge.class", "com/coolxer/synap/bridge/AgentBridge$Sink.class",
                "com/coolxer/synap/bridge/AgentBridge$Decision.class", "com/coolxer/synap/bridge/BridgeEvent.class",
                "com/coolxer/synap/bridge/RequestSecurityContext.class"};
        Path jar = Files.createTempFile("synap-bootstrap-bridge-", ".jar"); jar.toFile().deleteOnExit();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String resource : resources) {
                try (InputStream input = SynapJavaAgent.class.getClassLoader().getResourceAsStream(resource)) {
                    if (input == null) throw new IllegalStateException("missing bootstrap bridge resource: " + resource);
                    output.putNextEntry(new JarEntry(resource)); input.transferTo(output); output.closeEntry();
                }
            }
        }
        instrumentation.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(jar.toFile()));
    }
}
