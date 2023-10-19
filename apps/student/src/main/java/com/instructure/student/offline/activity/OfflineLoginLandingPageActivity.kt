package com.instructure.student.offline.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.instructure.canvasapi2.models.AccountDomain
import com.instructure.loginapi.login.databinding.ActivityLoginLandingPageBinding
import com.instructure.loginapi.login.util.QRLogin
import com.instructure.pandautils.analytics.SCREEN_VIEW_LOGIN_LANDING
import com.instructure.pandautils.analytics.ScreenView
import com.instructure.pandautils.binding.viewBinding
import com.instructure.pandautils.utils.ColorUtils
import com.instructure.pandautils.utils.ViewStyler
import com.instructure.pandautils.utils.setGone
import com.instructure.pandautils.utils.setVisible
import com.instructure.student.R
import com.instructure.student.activity.SignInActivity
import com.instructure.student.activity.StudentLoginWithQRActivity
import com.instructure.student.databinding.ActivityLoginTwouLandingPageBinding
import dagger.hilt.android.AndroidEntryPoint

@ScreenView(SCREEN_VIEW_LOGIN_LANDING)
@AndroidEntryPoint
class OfflineLoginLandingPageActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityLoginTwouLandingPageBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        applyTheme()

        with(binding) {
            qrLogin.setVisible()

            findMySchool.setText(R.string.login)

            findMySchool.setOnClickListener {
                startActivity(
                    SignInActivity.createIntent(
                        this@OfflineLoginLandingPageActivity,
                        AccountDomain(QRLogin.QR_DOMAIN_VALUE)
                    )
                )
            }

            qrLogin.setOnClickListener {
                startActivity(
                    Intent(
                        this@OfflineLoginLandingPageActivity,
                        StudentLoginWithQRActivity::class.java
                    )
                )
            }
        }
    }

    private fun applyTheme() {
        ViewStyler.themeStatusBar(this@OfflineLoginLandingPageActivity)
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, OfflineLoginLandingPageActivity::class.java)
        }
    }
}
