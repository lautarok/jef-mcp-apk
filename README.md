# SMS Forwarder - App Android

Aplicación nativa ligera para Android (SMS/Notification Forwarder) diseñada para ser compilada mediante línea de comandos (CLI) utilizando directamente la cadena de herramientas del SDK de Android y Kotlin, **sin depender de Gradle**.

Cuenta con una interfaz de usuario (*WebView*) a pantalla completa con tema oscuro (`#121212`), integración puente vía JavaScript (`AndroidBridge`) y gestión dinámica de reglas de filtrado y reenvío de mensajes.

---

## 🛠️ Requisitos del Sistema y Dependencias

Para compilar la aplicación se requieren las herramientas del sistema y del SDK de Android instaladas localmente:

### 1. Herramientas del Sistema (CLI)
* **Bash** (`bash`)
* **Java Development Kit (JDK):** versión 11 o superior (`keytool`, `javac`, etc.)
* **Compilador de Kotlin (`kotlinc`):** Instalable vía gestor de paquetes (p. ej. `apt install kotlinc`) o SDKMAN.
* **Librería estándar de Kotlin (`kotlin-stdlib.jar`):** Debe estar en alguna de las siguientes rutas habituales:
  * `/usr/share/kotlin/lib/kotlin-stdlib.jar`
  * `/usr/lib/kotlin/lib/kotlin-stdlib.jar`
  * `$HOME/.sdkman/candidates/kotlin/current/lib/kotlin-stdlib.jar`
* **Utilidad `zip`**

### 2. Componentes del Android SDK
* **Android SDK Command-line Tools / Build-tools:** `34.0.0`
* **Android Platform:** API Level `34` (`platforms/android-34/android.jar`)
* **Herramientas necesarias del SDK:**
  * `aapt2` (Compilación de recursos y manifest)
  * `d8` (Conversión de Java Bytecode `.class` a Dalvik Executable `.dex`)
  * `zipalign` (Alineación de memoria del APK)
  * `apksigner` (Firma digital de paquetes APK)

---

## 🏗️ Variables de Entorno

El script de compilación utiliza las siguientes variables (con valores por defecto si no están definidas):

| Variable | Descripción | Valor por Defecto |
| :--- | :--- | :--- |
| `ANDROID_HOME` | Ruta raíz del SDK de Android | `$HOME/Android` |
| `PLATFORM_JAR` | Ruta al JAR de la plataforma Android | `$SDK_ROOT/platforms/android-34/android.jar` |
| `BUILD_TOOLS` | Ruta a las build-tools de Android | `$SDK_ROOT/build-tools/34.0.0` |

*Ejemplo de exportación manual:*
```bash
export ANDROID_HOME="$HOME/Android/Sdk"
```

---

## 🚀 Proceso de Compilación (`build.sh`)

El archivo `build.sh` realiza la compilación completa de la aplicación sin Gradle en 5 pasos automáticos:

1. **Compilación de recursos (`aapt2`):** Empaqueta los recursos XML de `res/`, las vistas web de `assets/` y el `AndroidManifest.xml`, generando la clase `R.java`.
2. **Compilación de Kotlin (`kotlinc`):** Compila todo el código fuente nativo ubicado en `src/` junto con las clases generadas por `aapt2`.
3. **Conversión a DEX (`d8`):** Transforma el código `.class` de Kotlin y la librería estándar (`kotlin-stdlib.jar`) a archivos bytecode `.dex`.
4. **Empaquetado (`zip`):** Incorpora los archivos `classes*.dex` dentro del APK base.
5. **Alineación y Firma (`zipalign` + `apksigner`):** Alinea los archivos del paquete a 4 bytes y lo firma digitalmente con un Keystore de depuración generado automáticamente en `build/debug.keystore`.

### Comandos para Compilar

Dar permisos de ejecución y correr el script de compilación:

```bash
chmod +x build.sh
./build.sh
```

El APK final compilado, firmado y listo para instalar se generará en:
```text
build/jef-mcp.apk
```

### Instalación en Dispositivo via ADB

```bash
adb install -r build/jef-mcp.apk
```

---

## ⚙️ Arquitectura de la Aplicación

### Vistas y Navegación SPA (WebView)
La interfaz de usuario se ubica en `assets/index.html` y funciona como una aplicación de página única (SPA) con dos pantallas:

1. **Pantalla Principal (Configuración):**
   * Configuración de la URL base/endpoint del servidor.
   * Configuración del `Token / API Key` (`X-Api-Key`).
   * Accesos directos a ajustes del sistema de Android (Permisos de Notificaciones e Ignorar Optimización de Batería).

2. **Pantalla de Reglas de Mensajes:**
   * Carga de reglas desde el servidor mediante `GET /v1/config`.
   * Gestión dinámica (crear/eliminar) de reglas en grupos **`allow`** y **`prevent`**.
   * Guardado local (`SharedPreferences`) y sincronización con el servidor mediante `PUT /v1/config`.

### Restricciones de Propiedades
Las reglas de filtrado aceptan únicamente las siguientes propiedades como tipo `string`:
* **`content`:** Contenido textual del mensaje o notificación.
* **`platform`:** Nombre del paquete u origen de la aplicación/plataforma (ej: `prex`).
* **`sender`:** Remitente o emisor.

### Estructura de Payload HTTP

#### Petición `PUT /v1/config` (Envío de reglas)
```json
{
  "allow": [
    {
      "property": "platform",
      "include": "prex"
    },
    {
      "property": "content",
      "include": "$"
    }
  ],
  "prevent": [
    {
      "property": "platform",
      "include": "fake"
    }
  ]
}
```

#### Respuesta `GET /v1/config` (Lectura de reglas)
```json
{
  "message": {
    "creation": {
      "allow": [
        {
          "property": "platform",
          "includes": "prex"
        }
      ],
      "prevent": []
    }
  }
}
```

---

## 📁 Estructura del Proyecto

```text
.
├── assets/
│   └── index.html               # UI (HTML/CSS/JS) a pantalla completa
├── res/
│   └── values/
│       └── themes.xml           # Tema nativo sin ActionBar (android:Theme.Material.NoActionBar)
├── src/
│   └── com/ejemplo/smsforwarder/
│       └── MainActivity.kt      # Actividad principal, WebView y AndroidBridge (JavaInterface)
├── AndroidManifest.xml          # Configuración de permisos, tema y servicios
├── build.sh                     # Script Bash de compilación CLI (aapt2, kotlinc, d8, apksigner)
└── README.md                    # Documentación de la app
```