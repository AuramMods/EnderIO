# Cycle 235 — Conduit arrow depth

The connection box has side faces at model coordinates 6.5 and 9.5. The previous arrows sat just 0.0002 model units away, and both Push/Pull layers shared the same plane. The single-arrow models now sit 0.02 model units from the box. Push/Pull uses offsets of 0.02 and 0.04, so its two layers are also separated. These offsets are 0.00125 and 0.0025 blocks. The arrow textures, tints and visible footprint stay the same.

The packaged model inventory probe checks the actual baked face depth for input, output and Push/Pull on all four sides of the source model. The conduit renderer rotates those same models onto every connection. The probe repeats its checks after a resource reload.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain

port_runtime=build/port-runtime
port_profile=cycle-235-conduit-arrow-client
port_client="$port_runtime/$port_profile"
port_seed="$port_runtime/cycle-234-vat-fixed-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar enderio/build/libs/enderio-8.2.14.234-1.20.1+8a57d210d3.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' python3 scripts/porting/client/run_smoke.py --launch "$port_client/launch.json" \
  --scenario model-inventory --output build/port-evidence/cycle-235-conduit-arrows --timeout 180
```

## Results

- The product assembly and test-jar reobfuscation pass. Comparing the installed models with the previous package verifies that only the face-normal coordinates changed. The arrow textures, UVs and tint indices are unchanged. The tested production SHA-256 is `5be58f9c9a8ed366467357b879601521e0977be42eee86e4d7b2106ccf5a0c37`.
- The packaged Forge client passes 31 checks, including 12 face-depth checks covering all three overlay models both at startup and after resource reload. No model warnings or client errors are reported. The client records zero activations, foreground samples or onscreen samples over 841 observations. The capture confirms narration off and all ten volume categories at zero. See `build/port-evidence/cycle-235-conduit-arrows/summary.json`.

## Next verification

Record the separate two-player Hang Glider verification against the packaged Forge server.
