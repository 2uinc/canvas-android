package com.instructure.student.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Xml.Encoding
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.RemoteConfigPrefs
import com.instructure.student.databinding.ActivityFive9Binding
import com.instructure.student.view.NestedWebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Five9SupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFive9Binding
    private val name = ApiPrefs.user?.name.orEmpty()
    private val firstName = ApiPrefs.user?.sortableName?.split(",")?.last()?.trim().orEmpty()
    private val lastName = ApiPrefs.user?.sortableName?.split(",")?.first()?.trim().orEmpty()
    private val email = ApiPrefs.user?.run { primaryEmail ?: email }.orEmpty()
    private val segmentKey = RemoteConfigPrefs.getString(SEGMENT_KEY_TAG).orEmpty()
    private val five9ConfigId =
        RemoteConfigPrefs.getString(FIVE9_CONFIG_ID_TAG, DEFAULT_FIVE9_CONFIG_ID) ?: DEFAULT_FIVE9_CONFIG_ID
    private val personalInfo =
        RemoteConfigPrefs.getString(PERSONAL_INFO_TAG, DEFAULT_PERSONAL_INFO) ?: DEFAULT_PERSONAL_INFO

    private fun getFormData() = """[
        {
            "type": "static text",
            "formType": "both",
            "required": false,
            "cav": ""
        },
        {
            "label": "First Name",
            "cav": "contact.firstName",
            "formType": "both",
            "type": "text",
            "required": true,
            "readOnly": false,
            "pos": "",
            "value": "$firstName"
        },
        {
            "type": "static text",
            "formType": "both",
            "required": false
        },
        {
            "type": "text",
            "formType": "both",
            "required": true,
            "label": "Last Name",
            "cav": "contact.lastName",
            "value": "$lastName"
        },
        {
            "type": "static text",
            "formType": "both",
            "required": false,
            "label": ""
        },
        {
            "label": "University Email or Email Address on Record",
            "cav": "contact.email",
            "formType": "both",
            "type": "email",
            "required": true,
            "value": "$email"
        },
        {
            "type": "static text",
            "formType": "both",
            "required": false
        },
        {
            "label": "Question/Describe your Issue",
            "cav": "Question",
            "formType": "both",
            "type": "textarea",
            "required": true,
            "readOnly": false
        },
        {
            "type": "static text",
            "formType": "both",
            "required": false
        },
        {
            "type": "static text",
            "formType": "none",
            "required": false,
            "label": "Please note that Online Campus Support will process your personal information in accordance with its <a href=\"https://www.oneidentity.com/legal/privacy.aspx\" target=\"_blank\">privacy policy</a> <br><br/> You may receive transactional emails containing your chat conversation with Online Campus Support. <br> <br/>",
            "cav": ""
        }
    ]
    """

    private fun getFiveNineScript() = """
        <html>
            <body>
                <script src="https://live-chat.ps.five9.com/Five9ChatPlugin.js" type="text/javascript"></script>
                <script>
                  function callback(event) {
                    try {
                      javascript:window.android.logEvent(event.type);
                      switch (event.type) {
                        case 'initialized':
                          javascript:window.android.onFive9Opened();
                          break;
                        case 'endChatConfirmed':
                            javascript:window.android.onFive9Closed();
                            break;
                        case 'error':
                            javascript:window.android.logEvent(event.error);
                            if (event.error == "No active chat session") {
                                javascript:window.android.onFive9Closed();
                            }
                            break;
                        default:
                          break;
                      }
                    } catch (exception) {
                      javascript:window.android.logError(exception);
                    }
                  }
                  let options = {
                    "appId": "$APP_ID",
                    "configId": "$five9ConfigId",
                    "headless": true,
                    "startOpen": true,
                    "allowAttachments": false,
                    "hideMinimize": true,
                    "miniForm": true,
                    "subtitle": "Hello, $name",
                    "sendButtonText": "<img src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iaXNvLTg4NTktMSI/Pgo8IS0tIEdlbmVyYXRvcjogQWRvYmUgSWxsdXN0cmF0b3IgMTYuMC4wLCBTVkcgRXhwb3J0IFBsdWctSW4gLiBTVkcgVmVyc2lvbjogNi4wMCBCdWlsZCAwKSAgLS0+CjwhRE9DVFlQRSBzdmcgUFVCTElDICItLy9XM0MvL0RURCBTVkcgMS4xLy9FTiIgImh0dHA6Ly93d3cudzMub3JnL0dyYXBoaWNzL1NWRy8xLjEvRFREL3N2ZzExLmR0ZCI+CjxzdmcgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayIgdmVyc2lvbj0iMS4xIiBpZD0iQ2FwYV8xIiB4PSIwcHgiIHk9IjBweCIgd2lkdGg9IjUxMnB4IiBoZWlnaHQ9IjUxMnB4IiB2aWV3Qm94PSIwIDAgNTM1LjUgNTM1LjUiIHN0eWxlPSJlbmFibGUtYmFja2dyb3VuZDpuZXcgMCAwIDUzNS41IDUzNS41OyIgeG1sOnNwYWNlPSJwcmVzZXJ2ZSI+CjxnPgoJPGcgaWQ9InNlbmQiPgoJCTxwb2x5Z29uIHBvaW50cz0iMCw0OTcuMjUgNTM1LjUsMjY3Ljc1IDAsMzguMjUgMCwyMTYuNzUgMzgyLjUsMjY3Ljc1IDAsMzE4Ljc1ICAgIiBmaWxsPSIjY2JjYmNiIi8+Cgk8L2c+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPC9zdmc+Cg==' class='rcw-send-icon' alt='Send'>",
                    "sendButtonActiveText": "<img src='data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iaXNvLTg4NTktMSI/Pgo8IS0tIEdlbmVyYXRvcjogQWRvYmUgSWxsdXN0cmF0b3IgMTYuMC4wLCBTVkcgRXhwb3J0IFBsdWctSW4gLiBTVkcgVmVyc2lvbjogNi4wMCBCdWlsZCAwKSAgLS0+CjwhRE9DVFlQRSBzdmcgUFVCTElDICItLy9XM0MvL0RURCBTVkcgMS4xLy9FTiIgImh0dHA6Ly93d3cudzMub3JnL0dyYXBoaWNzL1NWRy8xLjEvRFREL3N2ZzExLmR0ZCI+CjxzdmcgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayIgdmVyc2lvbj0iMS4xIiBpZD0iQ2FwYV8xIiB4PSIwcHgiIHk9IjBweCIgd2lkdGg9IjUxMnB4IiBoZWlnaHQ9IjUxMnB4IiB2aWV3Qm94PSIwIDAgNTM1LjUgNTM1LjUiIHN0eWxlPSJlbmFibGUtYmFja2dyb3VuZDpuZXcgMCAwIDUzNS41IDUzNS41OyIgeG1sOnNwYWNlPSJwcmVzZXJ2ZSI+CjxnPgoJPGcgaWQ9InNlbmQiPgoJCTxwb2x5Z29uIHBvaW50cz0iMCw0OTcuMjUgNTM1LjUsMjY3Ljc1IDAsMzguMjUgMCwyMTYuNzUgMzgyLjUsMjY3Ljc1IDAsMzE4Ljc1ICAgIiBmaWxsPSIjY2JjYmNiIi8+Cgk8L2c+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPGc+CjwvZz4KPC9zdmc+Cg==' class='rcw-send-icon' alt='Send' style='filter: contrast(0)' >",
                    "contact": {
                      "email": "$email",
                      "name": "$name"
                    },
                    "formData": ${getFormData()}
                  };
                  options.callback = callback;
                  Five9ChatPlugin(options);
                </script>
            </body>
        </html>
    """.trimIndent()

    private fun getExpertScript() = """
         <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no, viewport-fit=cover">
                <link rel="stylesheet" href="https://chatbot-frontend.prod.ai.2u.com/@latest/index.min.css" />
                <style type="text/css">
                  .intercom-lightweight-app-launcher {
                    display: none !important;
                  }
                </style>
            </head>
            <body>
                <script>
                    window.XpertChatbotFrontend = {
                        xpertKey: '${EXPERT_KEY}',
                        configurations: {
                            chatApi: {
                                payloadParams: {
                                    use_case: '${USE_CASE}',
                                },
                            },
                            conversationScreen: {
                                liveChat: {
                                    options: {
                                        appId: '${APP_ID}',
                                    },
                                },
                            },
                        },
                    };
                </script>
                <script type="module" src="https://chatbot-frontend.prod.ai.2u.com/@latest/index.min.js"></script>
                <script>
                !function(){var i="analytics",analytics=window[i]=window[i]||[];if(!analytics.initialize)if(analytics.invoked)window.console&&console.error&&console.error("Segment snippet included twice.");else{analytics.invoked=!0;analytics.methods=["trackSubmit","trackClick","trackLink","trackForm","pageview","identify","reset","group","track","ready","alias","debug","page","screen","once","off","on","addSourceMiddleware","addIntegrationMiddleware","setAnonymousId","addDestinationMiddleware","register"];analytics.factory=function(e){return function(){if(window[i].initialized)return window[i][e].apply(window[i],arguments);var n=Array.prototype.slice.call(arguments);if(["track","screen","alias","group","page","identify"].indexOf(e)>-1){var c=document.querySelector("link[rel='canonical']");n.push({__t:"bpc",c:c&&c.getAttribute("href")||void 0,p:location.pathname,u:location.href,s:location.search,t:document.title,r:document.referrer})}n.unshift(e);analytics.push(n);return analytics}};for(var n=0;n<analytics.methods.length;n++){var key=analytics.methods[n];    analytics[key]=analytics.factory(key)}analytics.load=function(key,n){var t=document.createElement("script");t.type="text/javascript";t.async=!0;t.setAttribute("data-global-segment-analytics-key",i);t.src="https://cdn.segment.com/analytics.js/v1/" + key + "/analytics.min.js";var r=document.getElementsByTagName("script")[0];r.parentNode.insertBefore(t,    r);analytics._loadOptions=n};analytics._writeKey="$segmentKey";;analytics.SNIPPET_VERSION="5.2.0";
                    analytics.load("$segmentKey");
                    analytics.page();
                }}();
                </script>
                <script>
                    function observeButtons() {
                        var chatButton = document.getElementById("xpert_chatbot__floating-action-btn");
                        if (chatButton != undefined && chatButton.isClicked == undefined) {
                            setTimeout(() => {
                                chatButton.click();
                                javascript:window.android.onFive9Opened();                                       
                            }, 500);
                            chatButton.isClicked = true;
                        }
                        var xpertCloseButton = document.getElementsByClassName("xpert-chatbot-popup__header--btn-outline")[0]
                        if (xpertCloseButton != undefined) {
                            xpertCloseButton.addEventListener(
                                "click",
                                function(e) {
                                    javascript:window.android.onFive9Closed();
                                },
                                false
                            );
                        }
                        var five9OpenButton = document.getElementsByClassName("xpert-chatbot-popup__live-chat--btn-outline")[0]
                        if (five9OpenButton != undefined) {
                            five9OpenButton.addEventListener(
                                "click",
                                function(e) {
                                    javascript:window.android.shouldOpenFive9();
                                },
                                false
                            );
                        }
                    }
                    const config = { attributes: true, childList: true, subtree: true };
                    var callback = function(mutationsList) {
                        for(var mutation of mutationsList) {
                            if (mutation.type == 'childList') {
                                observeButtons();
                            }
                        }
                    };
                    var observer = new MutationObserver(callback);
                    observer.observe(document, config);
                </script>
            </body>
        </html>
    """

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityFive9Binding.inflate(layoutInflater)
        setContentView(binding.root)
        super.onCreate(savedInstanceState)
        setContent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setContent() {
        val webView = NestedWebView(this)
        val webSettings: WebSettings = webView.settings
        webSettings.builtInZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        webSettings.setSupportZoom(true)
        webSettings.displayZoomControls = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowContentAccess = true
        webSettings.domStorageEnabled = true

        webView.setBackgroundColor(Color.TRANSPARENT)
        if (isDarkModeOn() &&
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
        ) {
            WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
        }

        webView.addJavascriptInterface(object {

            @Suppress("unused")
            @JavascriptInterface
            fun onFive9Opened() {
                lifecycleScope.launch {
                    delay(1000)
                    binding.progressBar.visibility = View.GONE
                }
            }

            @Suppress("unused")
            @JavascriptInterface
            fun onFive9Closed() {
                finish()
            }

            @Suppress("unused")
            @JavascriptInterface
            fun logEvent(event: String) {
            }

            @Suppress("unused")
            @JavascriptInterface
            fun logError(event: String) {
            }

            @Suppress("unused")
            @JavascriptInterface
            fun onFive9Error() {
                finish()
            }

            @Suppress("unused")
            @JavascriptInterface
            fun shouldOpenFive9() {
                lifecycleScope.launch {
                    webView.loadDataWithBaseURL(
                        BASE_URL,
                        getFiveNineScript(),
                        null,
                        Encoding.UTF_8.name,
                        null,
                    )
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }, "android")
        webView.loadDataWithBaseURL(
            BASE_URL,
            getExpertScript(),
            null,
            Encoding.UTF_8.name,
            null,
        )
        binding.containerLayout.addView(webView)
    }

    private fun isDarkModeOn(): Boolean {
        val nightModeFlags: Int = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        const val TAG = "Five9Support"
        const val APP_ID = "2U Inc"
        const val USE_CASE = "Canvas_Student"
        const val EXPERT_KEY = "degrees-canvas-support"
        const val BASE_URL = "https://digitalcampus.instructure.com/"
        const val PERSONAL_INFO_TAG = "five9_formdata_label"
        const val SEGMENT_KEY_TAG = "chat_segment_key"
        const val FIVE9_CONFIG_ID_TAG = "five9_config_id"
        const val DEFAULT_FIVE9_CONFIG_ID = "GS | Support_Main_Flow_Xpert"
        const val DEFAULT_PERSONAL_INFO = """
            Please note that 2U will process your personal information in accordance with its <a href="https://essential.2u.com/privacy-policy" target="_blank">privacy policy</a> <br><br/> You may receive transactional emails containing your chat conversation with 2U. <br> <br/>
        """

        fun newInstance(context: Context) = Intent(context, Five9SupportActivity::class.java)
    }
}
