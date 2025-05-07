# 📦 yappy Payment Integration Module

Este módulo proporciona una solución completa para la **generación de códigos QR de pago** y la integración con plataformas como **Yappy** u otros servicios de pago en dispositivos Android.

Diseñado para integrarse fácilmente en sistemas POS, kioscos o aplicaciones móviles de pago, siguiendo las mejores prácticas de seguridad, modularidad y experiencia de usuario.

---

## 🚀 Características

- Apertura segura de sesión de dispositivo para pagos electrónicos.
- Generación de códigos QR dinámicos (`DYN`) para transacciones de pago.
- Manejo de errores de red con reintentos automáticos.
- Configuración dinámica de credenciales y endpoints.
- Almacenamiento seguro de configuraciones y tokens.
- Manejo de múltiples acciones a través de Intents personalizados (HioPOS Cloud compatible).
- Soporte para pantallas secundarias (`QrPresentation`).
- Código modular, escalable y preparado para SDK.

---

## 📦 Estructura del Módulo

com.example.yappy/ ├── errors/ # Manejo de errores globales ├── intenthandlers/ # Handlers de intents de transacción ├── network/ # Configuración de APIs y red ├── storage/ # Manejo de almacenamiento local y seguridad ├── ui/ │ ├── navigation/ # Navegación de pantallas │ ├── dialogs/ # Diálogos de configuración y resumen │ ├── screens/ # Pantallas principales │ ├── components/ # Componentes de UI reutilizables │ └── theme/ # Estilos de UI ├── presentation/ # Pantallas secundarias (presentations) ├── utils/ # Utilidades generales └── MainActivity.kt # Entrada principal de la app

yaml
Copy
Edit

---

## 🛠️ Integración

### 1. Configuración inicial

Configura las credenciales de API (API Key, Secret Key, Device ID, etc.) mediante el `ConfigDialog` o utilizando el `InitializeHandler` que recibe parámetros XML.

### 2. Flujo de transacción

- Llamar a `TransactionHandler.handle()` para iniciar una transacción.
- El sistema abrirá sesión, generará el QR y mostrará el código al usuario.
- Si se detectan errores de red, mostrará opciones de **Reintentar** o **Cancelar** con límite de reintentos.

### 3. Finalización

Al terminar, puedes ejecutar `FinalizeHandler.handle()` para liberar recursos (si aplica).

---

## 🔐 Seguridad

- Almacenamiento seguro de tokens mediante `LocalStorage` y `CryptoHelper`.
- Validación de configuraciones antes de iniciar transacciones.
- Manejo seguro de errores y protección contra reintentos infinitos.

---

## 🧩 Componentes Clave

| Componente | Descripción |
|:---|:---|
| `TransactionHandler` | Gestiona la apertura de sesión, generación de QR y muestra al usuario. |
| `InitializeHandler` | Recibe configuración inicial y guarda credenciales. |
| `FinalizeHandler` | Finaliza sesión de pago de forma segura. |
| `ErrorHandler` | Muestra errores configurables de red y de configuración. |
| `ErrorUtils` | Interpreta excepciones y categoriza mensajes de error. |
| `LocalStorage` | Guarda configuración y tokens en el dispositivo. |
| `ApiService` | Realiza solicitudes de red seguras para abrir sesión y generar QR. |
| `ConfigDialog` | Permite al usuario configurar o revisar sus credenciales de forma visual. |
| `QrPresentation` | Muestra QR de pago en pantallas externas si están disponibles. |

---

## ⚙️ Requerimientos

- Android API Level mínimo: **30**
- Kotlin **2.0.0** o superior
- Compose UI
- Librerías necesarias:
    - `Material 3`
    - `ZXing` para manejo QR
    - `DataStore Preferences`
    - `Navigation Compose`

---

## 🧪 Testeado en

- Android 11, 12, 13
- Dispositivos con y sin pantallas secundarias
- Emuladores y dispositivos reales (ARM64)

---

## 📄 Licencia

> Este módulo es propiedad de **[Tu Empresa o Nombre]**.  
> Prohibida su redistribución o modificación sin autorización previa.

---

