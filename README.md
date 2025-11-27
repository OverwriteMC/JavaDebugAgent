# Java Debug Agent

Агент для логирования вызовов методов на лету.

---

## Базовое использование

`java -javaagent:/path/to/agent-1.0.jar -jar myapp.jar`

---

## Аргументы агента

`-javaagent:setop-agent.jar="Class1:methodA|methodB,Class2:methodC"`

### Формат аргументов

`<ClassSimpleName>:<method1>|<method2>|<method3>,<AnotherClass>:<methodX>`

- **ClassSimpleName** — имя класса без пакета (например, `PlayerList`)  
- **method1, method2, …** — имена методов для патчинга (разделяются `|` или `+`)  
- Несколько классов разделяются запятой `,`

#### Пример

`-javaagent:setop-agent.jar=PlayerList:addOp|op,CraftEntity:setOp`

---

## Дополнительные настройки через системные свойства

### Вывод в консоль

-DdebugAgent.printToConsole=true - включить вывод в консоль  
-DdebugAgent.printToConsole=false - отключить вывод в консоль

### Вывод в файл

`-DdebugAgent.printToFile=/path/to/log.txt`

- Если путь не указан, вывод в файл отключен

---

## Значение по умолчанию

Если аргументы агента не переданы, агент патчит метод:

`PlayerList:addOp|op`

#### Пример длялоггирования с аргументами:

`-javaagent:agent-1.0.jar="PlayerList:addOp|op,SimpleCommandMap:dispatch"` - будет выводить добавление оператора в классе PlayerList, а также исполнение команды в классе SimpleCommandMap


### Пример вывода дебага:

```
[23:20:53] [Server thread/WARN]: [DEBUG-AGENT] Method dispatch was called!
[23:20:53] [Server thread/WARN]:  runtimeClass=org.bukkit.craftbukkit.v1_16_R3.command.CraftCommandMap
[23:20:53] [Server thread/WARN]: ---- method params ----
[23:20:53] [Server thread/WARN]: param0= com.destroystokyo.paper.console.TerminalConsoleCommandSender@43f121b6
[23:20:53] [Server thread/WARN]: param1= 123
[23:20:53] [Server thread/WARN]: ---- captured stack ----
[23:20:53] [Server thread/WARN]:  ru.overwrite.agent.Main.handlePatchedCall(Main.java:191)
[23:20:53] [Server thread/WARN]:  org.bukkit.command.SimpleCommandMap.dispatch(SimpleCommandMap.java)
[23:20:53] [Server thread/WARN]:  org.bukkit.craftbukkit.v1_16_R3.CraftServer.dispatchCommand(CraftServer.java:767)
[23:20:53] [Server thread/WARN]:  org.bukkit.craftbukkit.v1_16_R3.CraftServer.dispatchServerCommand(CraftServer.java:711)
[23:20:53] [Server thread/WARN]:  net.minecraft.server.v1_16_R3.DedicatedServer.handleCommandQueue(DedicatedServer.java:460)
[23:20:53] [Server thread/WARN]:  net.minecraft.server.v1_16_R3.DedicatedServer.b(DedicatedServer.java:428)
[23:20:53] [Server thread/WARN]:  net.minecraft.server.v1_16_R3.MinecraftServer.a(MinecraftServer.java:1382)
[23:20:53] [Server thread/WARN]:  net.minecraft.server.v1_16_R3.MinecraftServer.w(MinecraftServer.java:1112)
[23:20:53] [Server thread/WARN]:  net.minecraft.server.v1_16_R3.MinecraftServer.lambda$a$0(MinecraftServer.java:252)
[23:20:53] [Server thread/WARN]:  java.base/java.lang.Thread.run(Thread.java:1474)
[23:20:53] [Server thread/WARN]: 
```
