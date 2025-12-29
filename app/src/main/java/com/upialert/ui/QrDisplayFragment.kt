package com.upialert.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.upialert.databinding.DialogQrDisplayBinding
import com.upialert.utils.QrUtils

class QrDisplayFragment : BottomSheetDialogFragment() {

    private var _binding: DialogQrDisplayBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogQrDisplayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("upi_settings", android.content.Context.MODE_PRIVATE)
        val vpa = prefs.getString("vpa", "") ?: ""
        val name = prefs.getString("payee_name", "") ?: ""

        if (vpa.isEmpty()) {
            binding.tvPayeeName.text = "Setup Required"
            binding.tvVpa.text = "Please configure UPI ID in Settings"
            return
        }

        binding.tvPayeeName.text = name
        binding.tvVpa.text = vpa

        fun updateQr() {
            val amount = binding.etAmount.text.toString().trim()
            val upiUri = QrUtils.getUpiString(vpa, name, amount)
            val qrBitmap = QrUtils.generateQrCode(upiUri)
            if (qrBitmap != null) {
                binding.ivQrCode.setImageBitmap(qrBitmap)
            }
        }

        // Initial Generation
        updateQr()

        // Listener for Amount changes
        binding.etAmount.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateQr() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QrDisplayFragment"
    }
}
