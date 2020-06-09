#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# name of simulator (used to generate *.f arguments file)
#########################################################################################
sim_name = verilator

#########################################################################################
# vcs simulator types and rules
#########################################################################################
sim_prefix = simulator
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)-debug
ROM_FILE = $(build_dir)/$(long_name).rom.v
ROM_CONF_FILE = $(build_dir)/$(long_name).rom.conf
DTS_FILE = $(build_dir)/$(long_name).dts

PERMISSIVE_ON=
PERMISSIVE_OFF=

WAVEFORM_FLAG=-v$(sim_out_name).vcd

# This file is for simulation only. VLSI flows should replace this file with one containing hard SRAMs
MACROCOMPILER_MODE ?= --mode synflops

# The macro-compiler command creator. Just attach a buch of them, to avoid multiple-execution of sbt
ALL_TOPS := $(TOP) $(MODEL) $(shell echo $(SEPARE) | sed "s/,/ /g")
MACROCOMPILER_COMMANDS = $(foreach T, $(ALL_TOPS),"runMain barstools.macros.MacroCompiler -n $(build_dir)/$T.mems.conf -v $(build_dir)/$T.mems.v -f $(build_dir)/$T.mems.fir $(MACROCOMPILER_MODE)") 


.PHONY: default
# NOTE: We are going to add the true tapeout stuff here
default: my_verilog
	cp -rf $(TEEHW_DIR)/optvsrc/* $(build_dir)
	# We are going to separate the different tops here
	cd $(base_dir) && $(SBT) "project $(SBT_PROJECT)" "runMain $(MODEL_PACKAGE).uecutils.MultiTopAndHarness -o $(build_dir)/SHOULDNT.v -i $(FIRRTL_FILE) --syn-tops $(SEPARE) --chip-top $(TOP) --harness-top $(MODEL) --infer-rw --repl-seq-mem -c:$(TOP):-o:$(build_dir)/SHOULDNT.mems.conf -td $(build_dir)"
	rename.ul $(MODEL_PACKAGE).$(MODEL).$(CONFIG) $(MODEL) $(build_dir)/*
	# We also want to generate our memories
	cd $(base_dir) && $(SBT) "project barstoolsMacros" $(MACROCOMPILER_COMMANDS)

my_verilog: $(FIRRTL_FILE) $(ROM_FILE)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/hardware/chipyard/common.mk

#########################################################################################
# ROM generation
#########################################################################################
$(ROM_FILE): $(ROM_CONF_FILE) $(ROMGEN)
	make -C $(bootrom_dir) BUILD_DIR=$(build_dir) long_name=$(long_name) BOARD=$(BOARD) ROMGEN=$(ROMGEN) ROM_CONF_FILE=$(ROM_CONF_FILE) ROM_FILE=$(ROM_FILE) TEEHW=1 ISACONF=$(ISACONF) clean
	make -C $(bootrom_dir) BUILD_DIR=$(build_dir) long_name=$(long_name) BOARD=$(BOARD) ROMGEN=$(ROMGEN) ROM_CONF_FILE=$(ROM_CONF_FILE) ROM_FILE=$(ROM_FILE) TEEHW=1 ISACONF=$(ISACONF) romgen
ifeq ($(BOOTSRC),QSPI) #overwrite the rom.v with xip
	make -C $(xip_dir) BUILD_DIR=$(build_dir) long_name=$(long_name) ROMGEN=$(ROMGEN) ROM_CONF_FILE=$(ROM_CONF_FILE) ROM_FILE=$(ROM_FILE) ISACONF=$(ISACONF) romgen
endif

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(build_dir) $(sim_prefix)-*
	make -C $(bootrom_dir) clean
	make -C $(xip_dir) clean

