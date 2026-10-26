HW_NOTHING_PATH = hardware/nothing

PRODUCT_SOONG_NAMESPACES += $(HW_NOTHING_PATH)

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/common/private
BOARD_VENDOR_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/common/vendor
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/common/public

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/qcom/vendor
endif

$(call soong_config_set,camera,package_name,com.nothing.device.package_name)

ifneq ($(filter NothingEsimSwitcher ,$(PRODUCT_PACKAGES)),)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/esimswitcher/private
endif

ifneq ($(filter DeviceExtras ,$(PRODUCT_PACKAGES)),)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/DeviceExtras/private
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/DeviceExtras/public
endif

ifneq ($(filter HieroGlyph$(PRODUCT_DEVICE) ParanoidGlyph%,$(PRODUCT_PACKAGES)),)

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/glyph/private
BOARD_VENDOR_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/glyph/vendor
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/glyph/public

ifneq ($(filter HieroGlyph$(PRODUCT_DEVICE) ,$(PRODUCT_PACKAGES)),)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/glyph/HieroGlyph/private
endif

endif

ifneq ($(filter AssistKey ,$(PRODUCT_PACKAGES)),)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/AssistKey/private
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(HW_NOTHING_PATH)/sepolicy/AssistKey/public
endif

ifneq ($(filter android.hardware.vibrator.service.nothing-rt_ics, $(PRODUCT_PACKAGES)),)
$(call soong_config_set_bool, nothing_vibrator, enable_deps, true)
endif

