package com.sicepat.xrayapp.ui

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.databinding.ActivityThemeBinding
import com.sicepat.xrayapp.databinding.DialogColorPickerBinding
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.handler.SettingsManager

class ThemeActivity : BaseActivity() {

    private lateinit var binding: ActivityThemeBinding

    private var currentMode = "0" // "0"=Auto, "1"=Light, "2"=Dark
    private var currentScheme = "Content"
    private var isColorCustom = false
    private var currentSeedColor = "#2196F3"
    private var isPureBlack = false
    private var isTextScaleEnabled = false
    private var textScaleValue = 100f

    // List to store hex strings of custom user colors
    private val customColorsList = mutableListOf<String>()

    private val builtInSwatches = listOf(
        Triple("#FF8A80", "TonalSpot", 1),
        Triple("#D7CCC8", "Fidelity", 2),
        Triple("#2196F3", "Content", 3),
        Triple("#CDDC39", "FruitSalad", 4),
        Triple("#B2DFDB", "Neutral", 5),
        Triple("#4CAF50", "Vibrant", 6),
        Triple("#FFCCBC", "Expressive", 7),
        Triple("#9C27B0", "Rainbow", 8)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_theme))

        loadSettings()
        setupUI()
    }

    private fun loadSettings() {
        currentMode = MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        currentScheme = MmkvManager.decodeSettingsString(AppConfig.PREF_THEME_SCHEME, "Content") ?: "Content"
        isColorCustom = MmkvManager.decodeSettingsBool(AppConfig.PREF_THEME_COLOR_CUSTOM, false)
        currentSeedColor = MmkvManager.decodeSettingsString(AppConfig.PREF_THEME_COLOR_SEED, "#2196F3") ?: "#2196F3"
        isPureBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_THEME_PURE_BLACK, false)
        isTextScaleEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_THEME_TEXT_SCALING_ENABLED, false)
        textScaleValue = MmkvManager.decodeSettingsFloat(AppConfig.PREF_THEME_TEXT_SCALING_VALUE, 100f)

        // Load custom user colors from MMKV
        customColorsList.clear()
        val savedColors = MmkvManager.decodeSettingsString("pref_theme_custom_colors", "") ?: ""
        if (savedColors.isNotEmpty()) {
            customColorsList.addAll(savedColors.split(","))
        }
    }

    private fun saveCustomColors() {
        val joined = customColorsList.joinToString(",")
        MmkvManager.encodeSettings("pref_theme_custom_colors", joined)
    }

    private fun setupUI() {
        // 1. Theme mode selection cards
        updateThemeModeCards()
        binding.cardModeAuto.setOnClickListener { selectThemeMode("0") }
        binding.cardModeLight.setOnClickListener { selectThemeMode("1") }
        binding.cardModeDark.setOnClickListener { selectThemeMode("2") }

        // 2. Theme color controls
        binding.btnSchemePill.text = currentScheme
        binding.btnSchemePill.setOnClickListener { showSchemePickerDialog() }
        
        // Show confirmation dialog before resetting settings
        binding.btnResetTheme.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("tip")
                .setMessage("Make sure to reset")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm") { _, _ ->
                    resetThemeSettings()
                }
                .show()
        }

        // 3. Render swatches dynamically inside Grid
        populateSwatchesGrid()

        // 4. Pure Black Mode Switch
        binding.switchPureBlack.isChecked = isPureBlack
        binding.switchPureBlack.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_THEME_PURE_BLACK, isChecked)
            recreate()
        }

        // 5. Text Scaling Control
        binding.switchTextScaling.isChecked = isTextScaleEnabled
        binding.layoutTextScalingControl.visibility = if (isTextScaleEnabled) View.VISIBLE else View.GONE
        binding.sliderTextScaling.value = textScaleValue
        binding.txtTextScalingValue.text = "${textScaleValue.toInt()}%"

        binding.switchTextScaling.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_ENABLED, isChecked)
            binding.layoutTextScalingControl.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_VALUE, 100f)
            } else {
                MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_VALUE, binding.sliderTextScaling.value)
            }
            recreate()
        }

        binding.sliderTextScaling.addOnChangeListener { _, value, _ ->
            binding.txtTextScalingValue.text = "${value.toInt()}%"
            MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_VALUE, value)
        }

        binding.sliderTextScaling.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                recreate()
            }
        })
    }

    private fun populateSwatchesGrid() {
        binding.swatchesGrid.removeAllViews()

        // Build default swatches
        builtInSwatches.forEach { (hex, defaultScheme, index) ->
            val isActive = !isColorCustom && currentSeedColor.equals(hex, ignoreCase = true)
            val swatch = createSwatchView(
                hexColor = hex,
                isSelected = isActive,
                isCustom = false,
                onSelect = { selectSwatch(hex, defaultScheme, index) }
            )
            binding.swatchesGrid.addView(swatch)
        }

        // Build custom swatches
        customColorsList.forEachIndexed { i, hex ->
            val isActive = isColorCustom && currentSeedColor.equals(hex, ignoreCase = true)
            val swatch = createSwatchView(
                hexColor = hex,
                isSelected = isActive,
                isCustom = true,
                onSelect = { selectCustomSwatch(hex) },
                onEdit = { showColorPickerDialog(isEditingExisting = true, editIndex = i) }
            )
            binding.swatchesGrid.addView(swatch)
        }

        // Build Plus (+) Button
        val plusButton = createPlusButton {
            showColorPickerDialog(isEditingExisting = false)
        }
        binding.swatchesGrid.addView(plusButton)
    }

    private fun createSwatchView(
        hexColor: String,
        isSelected: Boolean,
        isCustom: Boolean,
        onSelect: () -> Unit,
        onEdit: (() -> Unit)? = null
    ): View {
        val density = resources.displayMetrics.density

        val frameLayout = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = (56 * density).toInt()
                height = (56 * density).toInt()
                setMargins((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            }
            isClickable = true
            isFocusable = true
        }

        // Outer circular background (Left half / Full background)
        val circleView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(R.drawable.ic_circle_solid)
            try {
                imageTintList = ColorStateList.valueOf(Color.parseColor(hexColor))
            } catch (e: Exception) {
                imageTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            }
        }
        frameLayout.addView(circleView)

        // Inner half circle (Right half)
        val halfCircleView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(R.drawable.ic_circle_half_right)
            try {
                val baseColor = Color.parseColor(hexColor)
                // Generate a secondary color: lighter version of base color or base color with 40% alpha
                val secondaryColor = androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, 100)
                imageTintList = ColorStateList.valueOf(secondaryColor)
            } catch (e: Exception) {
                imageTintList = ColorStateList.valueOf(Color.parseColor("#442196F3"))
            }
        }
        frameLayout.addView(halfCircleView)

        // Overlay element: Checkmark or Edit Pencil
        if (isSelected) {
            val checkView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                    android.view.Gravity.CENTER
                )
                setImageResource(R.drawable.ic_action_done)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            frameLayout.addView(checkView)
        } else if (isCustom && onEdit != null) {
            // White circular backing for the pencil badge
            val badgeBg = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (20 * density).toInt(),
                    (20 * density).toInt(),
                    android.view.Gravity.BOTTOM or android.view.Gravity.END
                )
                setImageResource(R.drawable.ic_circle_solid)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            frameLayout.addView(badgeBg)

            // Pencil icon centered inside the badge
            val badgePencil = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    android.view.Gravity.BOTTOM or android.view.Gravity.END
                ).apply {
                    setMargins(0, 0, (4 * density).toInt(), (4 * density).toInt())
                }
                setImageResource(R.drawable.ic_edit_24dp)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
            }
            frameLayout.addView(badgePencil)
        }

        // Action triggers
        frameLayout.setOnClickListener {
            if (isSelected) {
                if (isCustom) {
                    MaterialAlertDialogBuilder(this@ThemeActivity)
                        .setTitle("tip")
                        .setMessage("Are you sure you want to delete the current Color schemes?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Confirm") { _, _ ->
                            customColorsList.remove(hexColor)
                            saveCustomColors()
                            // Fallback to default
                            resetThemeSettings()
                        }
                        .show()
                }
            } else if (isCustom) {
                onSelect()
            } else {
                onSelect()
            }
        }
        
        frameLayout.setOnLongClickListener {
            if (isCustom) {
                onEdit?.invoke()
            }
            true
        }

        return frameLayout
    }

    private fun createPlusButton(onAdd: () -> Unit): View {
        val density = resources.displayMetrics.density
        val frameLayout = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = (56 * density).toInt()
                height = (56 * density).toInt()
                setMargins((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            }
            isClickable = true
            isFocusable = true
        }

        val primaryColor = try {
            Color.parseColor(currentSeedColor)
        } catch (e: Exception) {
            Color.parseColor("#2196F3")
        }

        val circleView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(R.drawable.ic_circle_solid)
            imageTintList = ColorStateList.valueOf(primaryColor)
        }
        frameLayout.addView(circleView)

        val plusIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (24 * density).toInt(),
                (24 * density).toInt(),
                android.view.Gravity.CENTER
            )
            setImageResource(R.drawable.ic_add_24dp)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        frameLayout.addView(plusIcon)

        frameLayout.setOnClickListener { onAdd() }
        return frameLayout
    }

    private fun selectThemeMode(mode: String) {
        if (currentMode == mode) return
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        SettingsManager.setNightMode()
        currentMode = mode
        updateThemeModeCards()
        recreate()
    }

    private fun updateThemeModeCards() {
        val primaryColor = try {
            Color.parseColor(currentSeedColor)
        } catch (e: Exception) {
            Color.parseColor("#2196F3")
        }

        val strokeSelected = 3 * resources.displayMetrics.density
        val strokeUnselected = 1 * resources.displayMetrics.density

        // Reset all cards
        binding.cardModeAuto.strokeColor = Color.parseColor("#E0E0E0")
        binding.cardModeAuto.strokeWidth = strokeUnselected.toInt()
        binding.cardModeAuto.setCardBackgroundColor(Color.TRANSPARENT)
        binding.imgModeAuto.imageTintList = ColorStateList.valueOf(Color.GRAY)
        binding.txtModeAuto.setTextColor(Color.GRAY)

        binding.cardModeLight.strokeColor = Color.parseColor("#E0E0E0")
        binding.cardModeLight.strokeWidth = strokeUnselected.toInt()
        binding.cardModeLight.setCardBackgroundColor(Color.TRANSPARENT)
        binding.imgModeLight.imageTintList = ColorStateList.valueOf(Color.GRAY)
        binding.txtModeLight.setTextColor(Color.GRAY)

        binding.cardModeDark.strokeColor = Color.parseColor("#E0E0E0")
        binding.cardModeDark.strokeWidth = strokeUnselected.toInt()
        binding.cardModeDark.setCardBackgroundColor(Color.TRANSPARENT)
        binding.imgModeDark.imageTintList = ColorStateList.valueOf(Color.GRAY)
        binding.txtModeDark.setTextColor(Color.GRAY)

        // Set selected card
        when (currentMode) {
            "0" -> {
                binding.cardModeAuto.strokeColor = primaryColor
                binding.cardModeAuto.strokeWidth = strokeSelected.toInt()
                binding.cardModeAuto.setCardBackgroundColor(Color.parseColor("#102196F3"))
                binding.imgModeAuto.imageTintList = ColorStateList.valueOf(primaryColor)
                binding.txtModeAuto.setTextColor(primaryColor)
            }
            "1" -> {
                binding.cardModeLight.strokeColor = primaryColor
                binding.cardModeLight.strokeWidth = strokeSelected.toInt()
                binding.cardModeLight.setCardBackgroundColor(Color.parseColor("#102196F3"))
                binding.imgModeLight.imageTintList = ColorStateList.valueOf(primaryColor)
                binding.txtModeLight.setTextColor(primaryColor)
            }
            "2" -> {
                binding.cardModeDark.strokeColor = primaryColor
                binding.cardModeDark.strokeWidth = strokeSelected.toInt()
                binding.cardModeDark.setCardBackgroundColor(Color.parseColor("#102196F3"))
                binding.imgModeDark.imageTintList = ColorStateList.valueOf(primaryColor)
                binding.txtModeDark.setTextColor(primaryColor)
            }
        }
    }

    private fun selectSwatch(hex: String, scheme: String, index: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_CUSTOM, false)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_SEED, hex)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_SCHEME, scheme)

        currentSeedColor = hex
        currentScheme = scheme
        isColorCustom = false

        binding.btnSchemePill.text = currentScheme
        recreate()
    }

    private fun selectCustomSwatch(hex: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_CUSTOM, true)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_SEED, hex)
        currentSeedColor = hex
        isColorCustom = true
        recreate()
    }

    private fun showSchemePickerDialog() {
        val schemes = arrayOf("TonalSpot", "Fidelity", "Monochrome", "Neutral", "Vibrant", "Expressive", "Content", "Rainbow", "FruitSalad")
        val checkedItem = schemes.indexOf(currentScheme)

        MaterialAlertDialogBuilder(this)
            .setTitle("Color schemes")
            .setSingleChoiceItems(schemes, checkedItem) { dialog, which ->
                val selectedScheme = schemes[which]
                MmkvManager.encodeSettings(AppConfig.PREF_THEME_SCHEME, selectedScheme)
                currentScheme = selectedScheme
                binding.btnSchemePill.text = selectedScheme
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun resetThemeSettings() {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, "0")
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_SCHEME, "Content")
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_CUSTOM, false)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_COLOR_SEED, "#2196F3")
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_PURE_BLACK, false)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_THEME_TEXT_SCALING_VALUE, 100f)
        
        // Clear custom colors on reset
        MmkvManager.encodeSettings("pref_theme_custom_colors", "")
        customColorsList.clear()

        SettingsManager.setNightMode()
        loadSettings()
        setupUI()
        recreate()
    }

    private fun showColorPickerDialog(isEditingExisting: Boolean, editIndex: Int = -1) {
        val dialogBinding = DialogColorPickerBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // Establish initial color
        val initialHex = if (isEditingExisting && editIndex in customColorsList.indices) {
            customColorsList[editIndex]
        } else {
            currentSeedColor
        }

        val initialColor = try {
            Color.parseColor(initialHex)
        } catch (e: Exception) {
            Color.parseColor("#2196F3")
        }

        // Load into picker and edit text
        dialogBinding.dialogColorPickerView.setColor(initialColor)
        dialogBinding.dialogColorHex.setText(initialHex.uppercase())

        // Connect Picker changes to Hex Input
        dialogBinding.dialogColorPickerView.listener = object : HsvColorPickerView.OnColorChangedListener {
            override fun onColorChanged(color: Int, hex: String) {
                dialogBinding.dialogColorHex.setText(hex.uppercase())
            }
        }

        // Connect Hex Input changes back to Picker View
        dialogBinding.dialogColorHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hexInput = s?.toString() ?: ""
                if (hexInput.length == 7 && hexInput.startsWith("#")) {
                    try {
                        val color = Color.parseColor(hexInput)
                        dialogBinding.dialogColorPickerView.setColor(color)
                    } catch (e: Exception) {
                        // ignore parsing during edits
                    }
                }
            }
        })

        dialogBinding.dialogBtnCancel.setOnClickListener { dialog.dismiss() }
        
        dialogBinding.dialogBtnConfirm.setOnClickListener {
            val finalHex = dialogBinding.dialogColorHex.text.toString().uppercase()
            try {
                Color.parseColor(finalHex) // validation check
                
                if (isEditingExisting && editIndex in customColorsList.indices) {
                    customColorsList[editIndex] = finalHex
                } else {
                    if (!customColorsList.contains(finalHex)) {
                        customColorsList.add(finalHex)
                    }
                }
                saveCustomColors()
                selectCustomSwatch(finalHex)
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this@ThemeActivity, "Invalid hex code!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
