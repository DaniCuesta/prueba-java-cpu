# prueba-java-cpu

Durante los primeros minutos, la aplicación **aumenta gradualmente el número de hilos activos**, generando más carga de **CPU** hasta alcanzar el número máximo configurado.  
Tras un tiempo definido (por defecto, **5 minutos**), los hilos se **detienen** y la CPU **desciende de forma natural a niveles mínimos**, **sin que el contenedor finalice**.
