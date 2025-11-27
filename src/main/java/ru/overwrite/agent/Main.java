package ru.overwrite.agent;

import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Debug/SetOp Java Agent — агент для логирования вызовов методов на лету.
 *
 * <p><b>Базовое использование:</b></p>
 * <pre>
 *   java -javaagent:/path/to/agent-1.0.jar -jar myapp.jar
 * </pre>
 *
 * <p><b>Аргументы агента:</b></p>
 * <pre>
 *   -javaagent:setop-agent.jar="Class1:methodA|methodB,Class2:methodC"
 * </pre>
 *
 * <p><b>Формат аргументов:</b></p>
 * <pre>
 *   &lt;ClassSimpleName&gt;:&lt;method1&gt;|&lt;method2&gt;|&lt;method3&gt;,
 *   &lt;AnotherClass&gt;:&lt;methodX&gt;
 * </pre>
 * - ClassSimpleName — имя класса без пакета (например, PlayerList).  
 * - method1, method2, … — имена методов для патчинга (можно разделять | или +).  
 * - Несколько классов разделяются запятой.
 *
 * <p><b>Пример:</b></p>
 * <pre>
 *   -javaagent:setop-agent.jar=PlayerList:addOp|op,CraftEntity:setOp
 * </pre>
 *
 * <p><b>Дополнительные настройки через системные свойства:</b></p>
 * <ul>
 *     <li><b>Вывод в консоль:</b>
 *         <pre>
 * -DdebugAgent.printToConsole=true  # включить вывод в консоль
 * -DdebugAgent.printToConsole=false # отключить вывод в консоль
 *         </pre>
 *     </li>
 *     <li><b>Вывод в файл:</b>
 *         <pre>
 * -DdebugAgent.printToFile=/path/to/log.txt
 *         </pre>
 *         Если путь не указан, вывод в файл отключен.
 *     </li>
 * </ul>
 *
 * <p>По умолчанию агент патчит метод <code>PlayerList:addOp|op</code>, если аргументы не переданы.</p>
 * 
 * <p>Красивое описание by chatgpt.
 */
public final class Main {

    private static final Map<String, Set<String>> TARGETS = new HashMap<>();

    private static final boolean PRINT_TO_CONSOLE = Boolean.parseBoolean(System.getProperty("debugAgent.printToConsole", "true"));

    private static final String FILE_PATH = System.getProperty("debugAgent.printToFile", null);

    public static void premain(String agentArgs, Instrumentation inst) {
        parseAgentArgs(agentArgs);

        System.err.println("[DEBUG-AGENT] premain start, args=" + agentArgs + ", targets=" + TARGETS);

        inst.addTransformer(new DebugTransformer(), true);

        if (inst.isRetransformClassesSupported()) {
            for (Class<?> cl : inst.getAllLoadedClasses()) {
                try {
                    String cn = cl.getName();
                    if (!isCandidateClass(cn)) {
                        continue;
                    }

                    if (inst.isModifiableClass(cl)) {
                        try {
                            inst.retransformClasses(cl);
                        } catch (Exception ex) {
                            System.err.println("[DEBUG-AGENT] retransform failed for " + cn + ": " + ex);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        } else {
            System.err.println("[DEBUG-AGENT] JVM does not support class retransformation.");
        }
    }

    private static void parseAgentArgs(String agentArgs) {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            System.err.println("[DEBUG-AGENT] no agent args → using defaults: PlayerList:addOp|op");
            loadDefaultTargets();
            return;
        }

        TARGETS.clear();

        String[] pairs = agentArgs.split(",");
        for (String p : pairs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split(":");
            if (parts.length < 2) {
                System.err.println("[DEBUG-AGENT] invalid target pair (skip): " + trimmed);
                continue;
            }

            String classSimpleName = parts[0].trim();
            String methodsPart = parts[1].trim();

            if (classSimpleName.isEmpty() || methodsPart.isEmpty()) {
                System.err.println("[DEBUG-AGENT] invalid target pair (empty parts) (skip): " + trimmed);
                continue;
            }
            
            String[] methodNames = methodsPart.split("[|+]");
            Set<String> methodSet = TARGETS.computeIfAbsent(classSimpleName, k -> new HashSet<>());

            for (String m : methodNames) {
                String mn = m.trim();
                if (!mn.isEmpty()) {
                    methodSet.add(mn);
                }
            }
        }

        System.err.println("[DEBUG-AGENT] loaded " + TARGETS.size() + " patched classes from args");
    }

    private static void loadDefaultTargets() {
        Set<String> methods = new HashSet<>();

        methods.add("addOp");
        methods.add("op");

        TARGETS.put("PlayerList", methods);
    }
    
    private static boolean isCandidateClass(String className) {
        if (className == null) {
            return false;
        }
        return !className.startsWith("java.") && !className.startsWith("javax.") && !className.startsWith("jdk.") && !className.startsWith("sun.") && !className.startsWith("com.sun.") && !className.startsWith("ru.overwrite.agent.");
    }

    public static void handlePatchedCall(Object instance, String className, String methodName, Object[] args) {
        Path logFile = (FILE_PATH != null && !FILE_PATH.isBlank()) ? Path.of(FILE_PATH) : null;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[DEBUG-AGENT] Method '").append(methodName).append("' was called!\n");

            Class<?> runtimeClass;
            sb.append(" runtimeClass=");
            if (instance != null) {
                runtimeClass = instance.getClass();
                sb.append(runtimeClass.getName()).append('\n');
            } else {
                try {
                    runtimeClass = Class.forName(className);
                    sb.append(runtimeClass.getName()).append(" (forName)\n");
                } catch (Exception ex) {
                    sb.append("N/A\n");
                }
            }

            int paramsCount = (args == null) ? 0 : args.length;
            sb.append("---- method params ----\n");
            if (paramsCount > 0) {
                for (int i = 0; i < paramsCount; i++) {
                    Object arg = args[i];
                    sb.append(" param").append(i).append("= ").append(argToString(arg)).append('\n');
                }
            } else {
                sb.append("N/A\n");
            }

            Throwable callStack = new Throwable("Captured call stack");
            StackTraceElement[] elements = callStack.getStackTrace();

            sb.append("---- captured stack ----\n");
            for (StackTraceElement el : elements) {
                sb.append(' ').append(el.toString()).append('\n');
            }

            String out = sb.toString();

            if (PRINT_TO_CONSOLE) {
                try {
                    System.err.println(out);
                } catch (Exception ignore) {
                }
            }

            if (logFile != null) {
                try {
                    Files.writeString(logFile, out, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception ex) {
                    if (PRINT_TO_CONSOLE) ex.printStackTrace();
                }
            }

        } catch (Exception ex) {
            try {
                ex.printStackTrace();
            } catch (Throwable ignore) {
            }
        }
    }

    private static String argToString(Object o) {
        if (o == null) {
            return "null";
        }

        Class<?> cls = o.getClass();
        if (!cls.isArray()) {
            return String.valueOf(o);
        }

        // I hate this... java 25 can fix this, but not a single person uses it :'(
        if (o instanceof Object[]) {
            return Arrays.deepToString((Object[]) o);
        }
        if (o instanceof int[]) {
            return Arrays.toString((int[]) o);
        }
        if (o instanceof long[]) {
            return Arrays.toString((long[]) o);
        }
        if (o instanceof short[]) {
            return Arrays.toString((short[]) o);
        }
        if (o instanceof byte[]) {
            return Arrays.toString((byte[]) o);
        }
        if (o instanceof char[]) {
            return Arrays.toString((char[]) o);
        }
        if (o instanceof boolean[]) {
            return Arrays.toString((boolean[]) o);
        }
        if (o instanceof float[]) {
            return Arrays.toString((float[]) o);
        }
        if (o instanceof double[]) {
            return Arrays.toString((double[]) o);
        }

        return String.valueOf(o);
    }

    private static class DebugTransformer implements ClassFileTransformer {
        private final ClassPool classPool = ClassPool.getDefault();

        @Override
        public byte[] transform(ClassLoader loader, String internalClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (internalClassName == null) {
                return null;
            }

            String className = internalClassName.replace('/', '.');

            if (!isCandidateClass(className)) {
                return null;
            }

            String simpleName;
            int lastDot = className.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleName = className.substring(lastDot + 1);
            } else {
                simpleName = className;
            }

            try {
                if (loader != null) {
                    classPool.insertClassPath(new LoaderClassPath(loader));
                }

                CtClass ctClass;
                try {
                    ctClass = classPool.get(className);
                } catch (NotFoundException nf) {
                    return null;
                }

                if (ctClass.isInterface() || ctClass.isAnnotation()) {
                    ctClass.detach();
                    return null;
                }

                boolean modified = false;

                Set<String> wantedMethods = TARGETS.get(simpleName);
                boolean useDefault = TARGETS.isEmpty();

                for (CtMethod method : ctClass.getDeclaredMethods()) {
                    String mName = method.getName();

                    boolean shouldPatch;
                    if (useDefault) {
                        shouldPatch = "setOp".equals(mName);
                    } else {
                        shouldPatch = (wantedMethods != null && wantedMethods.contains(mName));
                    }

                    if (!shouldPatch) {
                        continue;
                    }

                    int mods = method.getModifiers();
                    if (Modifier.isAbstract(mods) || Modifier.isNative(mods)) {
                        continue;
                    }

                    try {
                        boolean isStatic = Modifier.isStatic(mods);
                        String instanceExpr = isStatic ? "null" : "this";

                        String injected =
                                "{ ru.overwrite.agent.Main.handlePatchedCall("
                                        + instanceExpr + ", \"" + ctClass.getSimpleName() + "\", \""
                                        + method.getName() + "\", $args); }";

                        method.insertBefore(injected);
                        modified = true;
                    } catch (Exception ex) {
                        System.err.println("[DEBUG-AGENT] failed to instrument method " + className + "." + mName + ": " + ex);
                    }
                }

                if (modified) {
                    byte[] newBytes = ctClass.toBytecode();
                    ctClass.detach();
                    System.err.println("[DEBUG-AGENT] instrumented " + className + " (methods patched)");
                    return newBytes;
                } else {
                    ctClass.detach();
                    return null;
                }
            } catch (Exception ex) {
                System.err.println("[DEBUG-AGENT] transform failed for " + className + ": " + ex);
                return null;
            }
        }
    }
}
