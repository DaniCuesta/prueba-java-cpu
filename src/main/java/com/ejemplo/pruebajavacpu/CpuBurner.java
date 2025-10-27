package com.example.pruebajavacpu;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CpuBurner {

    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private static volatile boolean running = true; // controla la ejecución de los hilos

    public static void main(String[] args) throws InterruptedException {
        int maxCores = Integer.parseInt(System.getenv().getOrDefault("MAX_CORES", "4"));
        int stepSeconds = Integer.parseInt(System.getenv().getOrDefault("STEP_SECONDS", "10"));
        int metricSeconds = Integer.parseInt(System.getenv().getOrDefault("METRIC_SECONDS", "10"));
        int durationSeconds = Integer.parseInt(System.getenv().getOrDefault("DURATION_SECONDS", "300")); // 5 min por defecto

        System.out.printf("[%s] Iniciando carga progresiva de CPU (hasta %d hilos, cada %d s, duración total %d s)%n",
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

        // Lanzar hilos de forma progresiva
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

        // Bucle principal de métricas
        while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
            TimeUnit.SECONDS.sleep(metricSeconds);

            long now = System.nanoTime();
            double intervalSec = (now - prevTimestamp) / 1e9;

            double processCpu = osBean.getProcessCpuLoad() * 100;
            double systemCpu = osBean.getSystemCpuLoad() * 100;
            long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long maxMem = runtime.maxMemory() / (1024 * 1024);

            System.out.printf("[%s] metrics {\"cpu_process\": %.2f, \"cpu_system\": %.2f, \"memory_used_mb\": %d, \"memory_max_mb\": %d, \"threads\": %d}%n",
                    Instant.now(), processCpu, systemCpu, usedMem, maxMem, Thread.activeCount());

            for (Thread t : threads) {
                long id = t.getId();
                long currentCpuTime = threadMXBean.getThreadCpuTime(id);
                long prevTime = prevCpuTime.getOrDefault(id, 0L);
                long deltaNs = currentCpuTime - prevTime;
                double threadCpuPercent = (deltaNs / (intervalSec * 1_000_000_000L)) * 100 / available;
                System.out.printf("[%s] Hilo ID=%d -> uso_cpu_aprox: %.2f%%%n",
                        Instant.now(), id, threadCpuPercent);
                prevCpuTime.put(id, currentCpuTime);
            }

            prevTimestamp = now;
        }

        // Detener los hilos
        running = false;
        System.out.printf("[%s] Tiempo de carga (%d s) completado. Deteniendo hilos...%n",
                Instant.now(), durationSeconds);

        for (Thread t : threads) {
            t.join(1000); // esperar hasta 1s por hilo
        }

        System.out.printf("[%s] Todos los hilos detenidos. Carga finalizada.%n", Instant.now());
    }

    // Bucle de carga de CPU controlado
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
}

