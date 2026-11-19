PVVX ?= deps/TlsrComProg825x/Uartfloader
TC32 ?= deps/ATC_TLSR_Paper/Firmware/tc32_linux/bin
BUILD ?= build

SOURCES = main.c $(PVVX)/Source/analog.c $(PVVX)/Source/flash.c $(PVVX)/mcu/cstartup_825x_floader.S
FLAGS = -Os -Wall -std=gnu99 -I$(PVVX)/Include -fdata-sections -ffunction-sections \
        -fpack-struct -fshort-enums -finline-small-functions -fshort-wchar -fms-extensions
LINK = -Wl,--gc-sections -T $(PVVX)/mcu/boot_825x.link -nostartfiles -nostdlib

../src/generated/NebularRamImage.h: $(BUILD)/nebular.bin embed.py
	python3 embed.py $(BUILD)/nebular.bin $@

$(BUILD)/nebular.bin: $(BUILD)/nebular.elf
	$(TC32)/tc32-elf-objcopy -O binary $< $@

$(BUILD)/nebular.elf: $(SOURCES) ../src/NebularProtocol.h
	mkdir -p $(BUILD)
	$(TC32)/tc32-elf-gcc $(FLAGS) $(SOURCES) $(LINK) -o $@
	$(TC32)/tc32-elf-size $@
	$(TC32)/tc32-elf-nm $@ | python3 -c 'import sys; s = {n: int(v, 16) for v, _, n in (l.split() for l in sys.stdin if len(l.split()) == 3)}; end = s["_end_bss_"]; assert 0x840000 <= end <= 0x846000, hex(end)'
