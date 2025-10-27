package com.example.pruebajavacpu;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CpuBurner {

    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private static volatile boolean running = true; // controla los hilos

    public static void main(String[] args) throws InterruptedException {
        int maxCores = Integer.parseInt(System.getenv().getOrDefault("MAX_CORES", "4"));
        int stepSeconds = Integer.parseInt(System.getenv().getOrDefault("STEP_SECONDS", "10"));
        int metricSeconds = Integer.parseInt(System.getenv().getOrDefault("METRIC_SECONDS", "10"));
        int durationSeconds = Integer.parseInt(System.getenv().getOrDefault("DURATION_SECONDS", "300")); // 5 min por defecto

        System.out.printf("[%s] Iniciando carga progresiva de CPU (hasta %d hilos, cada %d s, duración %d s)%n",
                Instant.now(), maxCores, stepSeconds, durationSeconds);

        int available = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String javaVersion = System.getProperty("java.version");
        System.out.printf("[%s] JVM detecta %d cores, memoria máxima %.0f MiB, versión Java %s%n",
                Instant.now(), available, (double) maxMemory, javaVersion);

        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        Runtime runtime = Runtime.getRuntime();

        if (threadMXBean.isThreadCpuTimeSupported() && !threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }

        List<Thread> threads = new ArrayList<>();
        Map<Long, Long> prevCpuTime = new HashMap<>();
        long prevTimestamp = System.nanoTime();

        // Lanzar hilos progresivamente
        for (int i = 1; i <= maxCores; i++) {
            final int logicalId = i;
            Thread t = new Thread(() -> cpuBurn(logicalId));
            t.setDaemon(true);
            t.start();
            threads.add(t);
            System.out.printf("[%s] Hilo lógico %d iniciado (Thread ID JVM=%d)%n",
                    Instant.now(), logicalId, t.getId());
            prevCpuTime.put(t.getId(), threadMXBean.getThreadCpuTime(t.getId()));
            TimeUnit.SECONDS.sleep(stepSeconds);
        }

        long startTime = System.currentTimeMillis();

        // Bucle principal de métricas mientras los hilos están activos
        while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
            printMetrics(metricSeconds, osBean, runtime, threads, prevCpuTime);
        }

        // Señal para detener los hilos
        running = false;
        System.out.printf("[%s] Fin del periodo de carga (%d s). Hilos detenidos, CPU debería descender ahora.%n",
                Instant.now(), durationSeconds);

        for (Thread t : threads) {
            t.join(1000);
        }

        // Fase de reposo pasivo (contenedor sigue vivo sin usar CPU)
        System.out.printf("[%s] Entrando en modo inactivo. Contenedor permanece ejecutándose.%n", Instant.now());
        while (true) {
            printMetrics(metricSeconds, osBean, runtime, threads, prevCpuTime);
            TimeUnit.SECONDS.sleep(metricSeconds);
        }
    }

    private static void cpuBurn(int logicalId) {
        double x = 0.0001;
        while (running) {
            x = Math.sin(x) * Math.cos(x) * Math.tan(x);
            if (Double.isNaN(x) || Double.isInfinite(x)) {
                x = 0.0001;
            }
        }
        System.out.printf("[%s] Hilo lógico %d detenido%n", Instant.now(), logicalId);
    }

    private static void printMetrics(int metricSeconds, OperatingSystemMXBean osBean, Runtime runtime,
                                     List<Thread> threads, Map<Long, Long> prevCpuTime) throws InterruptedException {
        TimeUnit.SECONDS.sleep(metricSeconds);
        long now = System.nanoTime();

        double processCpu = osBean.getProcessCpuLoad() * 100;
        double systemCpu = osBean.getSystemCpuLoad() * 100;
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        System.out.printf("[%s] metrics {\"cpu_process\": %.2f, \"cpu_system\": %.2f, \"memory_used_mb\": %d, \"memory_max_mb\": %d, \"threads\": %d}%n",
                Instant.now(), processCpu, systemCpu, usedMem, maxMem, Thread.activeCount());

        for (Thread t : threads) {
            long id = t.getId();
            long cpuTime = threadMXBean.getThreadCpuTime(id);
            if (cpuTime > 0) {
                System.out.printf("[%s] Hilo ID=%d -> tiempo_cpu_ns: %d%n", Instant.now(), id, cpuTime);
            }
        }
    }
}

