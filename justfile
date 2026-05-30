alias bk := build_ksud
alias bm := build_manager

build_ksud:
    cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml

build_manager: build_ksud
    cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
    cd manager && ./gradlew aDebug

build_rezygisk:
    cd ReZygisk && make release
    for arch in arm64-v8a x86_64; do \
      mkdir -p manager/app/src/main/jniLibs/$$arch; \
      cp ReZygisk/build/obj/release/loader/$$arch/stripped/libzygisk_ptrace.so \
        manager/app/src/main/jniLibs/$$arch/libzygisk-ptrace64.so; \
      cp ReZygisk/build/obj/release/zygiskd/$$arch/zygiskd \
        manager/app/src/main/jniLibs/$$arch/libzygiskd64.so; \
      cp ReZygisk/build/obj/release/loader/$$arch/stripped/libzygisk.so \
        manager/app/src/main/jniLibs/$$arch/libzygisk.so; \
    done
    mkdir -p manager/app/src/main/jniLibs/arm64-v8a
    cp ReZygisk/build/obj/release/loader/armeabi-v7a/stripped/libzygisk_ptrace.so \
      manager/app/src/main/jniLibs/arm64-v8a/libzygisk-ptrace32.so
    cp ReZygisk/build/obj/release/zygiskd/armeabi-v7a/zygiskd \
      manager/app/src/main/jniLibs/arm64-v8a/libzygiskd32.so
    mkdir -p manager/app/src/main/jniLibs/armeabi-v7a
    cp ReZygisk/build/obj/release/loader/armeabi-v7a/stripped/libzygisk_ptrace.so \
      manager/app/src/main/jniLibs/armeabi-v7a/libzygisk-ptrace32.so
    cp ReZygisk/build/obj/release/zygiskd/armeabi-v7a/zygiskd \
      manager/app/src/main/jniLibs/armeabi-v7a/libzygiskd32.so
    cp ReZygisk/build/obj/release/loader/armeabi-v7a/stripped/libzygisk.so \
      manager/app/src/main/jniLibs/armeabi-v7a/libzygisk.so

clippy:
    cargo fmt --manifest-path ./userspace/ksud/Cargo.toml
    cross clippy --target x86_64-pc-windows-gnu --release --manifest-path ./userspace/ksud/Cargo.toml
    cross clippy --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml
