# Creador PDF

Repositorio de Creador PDF para Android y de las paginas legales publicadas con GitHub Pages.

## Contenido

- `android/`: codigo fuente de la aplicacion Android.
- `apps/creador-pdf/`: ficha web y politica de privacidad usada por Google Play.
- `index.html`: indice de las politicas publicadas.

## Abrir la aplicacion

1. Abre la carpeta `android` con Android Studio.
2. Espera a que Gradle termine la sincronizacion.
3. Agrega `android/app/google-services.json` desde el proyecto Firebase `creadorpdf-fa0bb`.
4. Ejecuta la variante `debug` en un telefono o emulador Android.

El archivo de Firebase se puede recuperar con la cuenta autorizada usando:

```powershell
npx -y firebase-tools@latest apps:sdkconfig ANDROID 1:647628561796:android:2de6cd7e4e8094706e11d1 --project creadorpdf-fa0bb > android/app/google-services.json
```

## Crear una version firmada

Crea `android/local.properties` y conserva las credenciales fuera de GitHub:

```properties
sdk.dir=C:\\Users\\USUARIO\\AppData\\Local\\Android\\Sdk
KEYSTORE_FILE=app/creadorpdf-upload-2026.jks
KEYSTORE_PASSWORD=CONTRASENA_PRIVADA
KEY_ALIAS=upload
KEY_PASSWORD=CONTRASENA_PRIVADA
```

Despues coloca la clave de subida dentro de `android/app/` y genera el paquete:

```powershell
cd android
.\gradlew.bat bundleRelease
```

Los archivos de Firebase, claves, contrasenas, APK, AAB y configuraciones locales estan excluidos del repositorio.
