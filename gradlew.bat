@echo off
set DEFAULT_JVM_OPTS=
set CLASSPATH=%~dp0\gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=java.exe
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
