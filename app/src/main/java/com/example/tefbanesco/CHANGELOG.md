# 📦 CHANGELOG - tefbanesco Payment Integration Module

Registro de cambios del módulo de integración de pagos QR.

---

## [1.0.0] - 2024-04-28

### 🚀 Primera Versión Estable

- Estructura completa del módulo organizada en carpetas:
    - `errors/`
    - `intenthandlers/`
    - `network/`
    - `storage/`
    - `ui/`
    - `utils/`
    - `presentation/`
- Implementado flujo de inicialización (`InitializeHandler`) para cargar configuración desde XML.
- Implementado flujo de transacción (`TransactionHandler`) para abrir sesión, generar QR, mostrar en pantalla secundaria o fallback.
- Manejo de errores de red:
    - Detección de Timeout.
    - Detección de pérdida de internet.
    - Manejo de errores de red general.
    - Manejo de error desconocido.
- Funcionalidad de reintentos automáticos (máx. 3 intentos).
- Manejo de errores de configuración si falta el `BASE_URL`.
- Almacenamiento seguro de credenciales y token de sesión con `LocalStorage`.
- Compatibilidad con pantallas secundarias mediante `QrPresentation`.
- Manejo modularizado de errores (`ErrorHandler`) y categorización de excepciones (`ErrorUtils`).
- Diálogo de configuración (`ConfigDialog`) para setup visual de API Key, Secret Key, Device Info.
- Navegación con `Navigation Compose` para flujo de pantallas.
- Documentación inicial del proyecto (`README.md`).
- Preparado para empaquetarse como `.aar` o publicarse en repositorios privados Maven.
- Basado en Kotlin 2.0 y Jetpack Compose.

---

## [Futuro Próximo] - Roadmap 🚀

- [ ] Implementar tests unitarios para `LocalStorage`, `ConfigManager`, `ErrorUtils`.
- [ ] Implementar instrumented tests de flujo completo de generación de QR.
- [ ] Agregar control de sesión persistente para reconexiones.
- [ ] Crear plantilla de error customizable para el cliente final.
- [ ] Agregar animaciones de carga en generación de QR.
- [ ] Internacionalización (i18n) - soporte para múltiples idiomas.

---

# 📅 Formato de versiones

> Utiliza el esquema de versionado **SemVer**:
>
> **MAJOR.MINOR.PATCH**
>
> - **MAJOR**: Cambios incompatibles.
> - **MINOR**: Nuevas funciones compatibles.
> - **PATCH**: Correcciones de errores y mejoras internas.

---
