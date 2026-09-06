#!/bin/bash
# Avvia VoidReach CRM.
#
# Non si usa "mvn javafx:run": il plugin javafx 0.0.8 mette sul module path
# solo gli artefatti il cui nome inizia con "javafx", quindi esclude
# org.openjfx:jdk-jsobject. Dal JDK 26 quel modulo non fa piu' parte del JDK
# ed e' richiesto da javafx.web, percio' l'avvio fallisce con
# "Module jdk.jsobject not found, required by javafx.web".
# Qui il module path viene costruito a mano dai jar con classifier di piattaforma.
#
# Lo script gira anche su Windows dentro Git Bash. La' il classpath prodotto da
# Maven usa ';' come separatore e i percorsi contengono la lettera di unita'
# (es. "C:\..."), percio' separatore e classifier vanno scelti per piattaforma:
# con i due punti fissi i percorsi verrebbero spezzati sulla lettera di unita'.

set -euo pipefail
cd "$(dirname "$0")"

case "$(uname -s)" in
    Darwin) SEP=:
            case "$(uname -m)" in
                arm64) CLASSIFIER=mac-aarch64 ;;
                *)     CLASSIFIER=mac ;;
            esac ;;
    Linux)  SEP=:
            case "$(uname -m)" in
                aarch64|arm64) CLASSIFIER=linux-aarch64 ;;
                *)             CLASSIFIER=linux ;;
            esac ;;
    *)      # MINGW*, MSYS*, CYGWIN*: Git Bash e ambienti simili su Windows.
            SEP=';'
            CLASSIFIER=win ;;
esac

# I .class sono compilati con <maven.compiler.release> del pom, quindi serve un
# JDK almeno di quella major. Il "java" sul PATH puo' essere piu' vecchio di
# quello che Maven usa via JAVA_HOME, percio' JAVA_HOME viene provato per primo.
REQUIRED=$(sed -n 's:.*<maven\.compiler\.release>\([0-9]*\)</maven\.compiler\.release>.*:\1:p' pom.xml | head -1)
REQUIRED=${REQUIRED:-26}

java_major() {
    "$1" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1
}

CANDIDATES=()
if [ -n "${JAVA_HOME:-}" ]; then
    CANDIDATES+=("$JAVA_HOME/bin/java")
fi
CANDIDATES+=(java)

JAVA=""
for candidate in "${CANDIDATES[@]}"; do
    major=$(java_major "$candidate" 2>/dev/null || true)
    if [ -n "$major" ] && [ "$major" -ge "$REQUIRED" ]; then
        JAVA=$candidate
        break
    fi
done

if [ -z "$JAVA" ]; then
    echo "Serve un JDK $REQUIRED o superiore per avviare VoidReach." >&2
    echo "Imposta JAVA_HOME su un JDK $REQUIRED+ oppure mettilo sul PATH." >&2
    exit 1
fi

CP_FILE="target/runtime-classpath.txt"
mvn -q compile dependency:build-classpath \
    -Dmdep.outputFile="$CP_FILE" -DincludeScope=runtime

CP=$(cat "$CP_FILE")
MP=$(tr "$SEP" '\n' <<< "$CP" \
     | grep -E "(javafx-|jdk-jsobject).*${CLASSIFIER}\.jar$" \
     | paste -sd"$SEP" -)

if [ -z "$MP" ]; then
    echo "Nessun jar JavaFX trovato per il classifier '${CLASSIFIER}'." >&2
    exit 1
fi

JAVA_OPTS=(
    --module-path "$MP"
    --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media
    --enable-native-access=javafx.graphics
)

if [ "$(uname -s)" = "Darwin" ]; then
    JAVA_OPTS+=(
        -Xdock:name=VoidReach
        -Xdock:icon=src/main/packaging/macos/VoidReach-v2.icns
        -Dapple.awt.application.name=VoidReach
    )
fi

exec "$JAVA" "${JAVA_OPTS[@]}" -cp "target/classes${SEP}${CP}" com.crm.app.AppLauncher
