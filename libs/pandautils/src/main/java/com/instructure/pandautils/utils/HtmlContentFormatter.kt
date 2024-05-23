/*
 * Copyright (C) 2021 - present Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.instructure.pandautils.utils

import android.content.Context
import android.net.Uri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.instructure.canvasapi2.managers.OAuthManager
import com.instructure.canvasapi2.models.AuthenticatedSession
import com.instructure.canvasapi2.utils.weave.apiAsync
import com.instructure.pandautils.R
import com.instructure.pandautils.discussions.DiscussionHtmlTemplates
import com.instructure.pandautils.views.CanvasWebView
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Class that handles different kind of html content modifications, in case we have Iframes,
 * because the Android WebView can't handle it well. Implementation is based on the [WebWiewExtensions.kt]
 */
class HtmlContentFormatter(
    private val context: Context,
    private val crashlytics: FirebaseCrashlytics,
    private val oAuthManager: OAuthManager
) {

    suspend fun formatHtmlWithIframes(html: String): String {
        try {
            var newHTML = html
            val document = Jsoup.parse(newHTML)
            document.getElementsByTag("iframe").forEach { element ->
                if (isWistiaIFrame(element.outerHtml())) {
                    // Replace the Wistia iFrame with the Wistia divs
                    var stringToReplace = ""
                    val v1Link = "https://fast.wistia.com/assets/external/E-v1.js"
                    if (!newHTML.contains(v1Link)) {
                        stringToReplace += "<script src=\"$v1Link\" async></script>\n"
                    }
                    val transcriptLink = "https://fast.wistia.net/assets/external/transcript.js"
                    if (!newHTML.contains(transcriptLink)) {
                        stringToReplace += "<script src=\"$transcriptLink\" async></script>\n"
                    }

                    val srcUri = Uri.parse(element.attr("src"))
                    val iFrameIndex = srcUri.pathSegments.indexOf("iframe")
                    if (iFrameIndex != -1 && srcUri.pathSegments.size > iFrameIndex + 1) {
                        val wistiaId = srcUri.pathSegments[iFrameIndex + 1]
                        stringToReplace += "<script src=\"//fast.wistia.com/embed/medias/$wistiaId.jsonp\" async></script><div class=\"wistia_embed wistia_async_$wistiaId\" style=\"margin-top:10px;height:100%;width:100%\"></div>\n"
                        val wistiaTranscriptionTag =
                            "<wistia-transcript media-id=\"$wistiaId\" style=\"padding-top: 40px;height:400px;\"></wistia-transcript>"
                        val parent = element.parent()
                        var isTranscriptIncluded = false
                        if (parent.attr("class") == "wistia_responsive_wrapper") {
                            val parentOfParent = parent.parent()
                            if (parentOfParent.attr("class") == "wistia_responsive_padding") {
                                parentOfParent.after(wistiaTranscriptionTag)
                                isTranscriptIncluded = true
                            }
                        }
                        if (!isTranscriptIncluded) {
                            stringToReplace = "<div class=\"wistia_responsive_padding\" style=\"padding: 56.25% 0 0 0; position: relative;\"> \n" +
                                    "<div class=\"wistia_responsive_wrapper\" style=\"height: 100%; left: 0; position: absolute; top: 0; width: 100%;\">$stringToReplace</div></div>" +
                                    wistiaTranscriptionTag
                        }
                        element.replaceWith(Jsoup.parse(stringToReplace))

                        newHTML = document.html()
                    }
                }
            }

            newHTML = newHTML.replace("<#root>", "")

            if (newHTML.contains("<iframe")) {
                // First we need to find LTIs by looking for iframes
                val iframeMatcher = Pattern.compile("<iframe(.|\\n)*?iframe>").matcher(newHTML)

                while (iframeMatcher.find()) {
                    val iframe = iframeMatcher.group(0) ?: ""
                    // We found an iframe, we need to do a few things...
                    val matcher = Pattern.compile("src=\"([^\"]+)\"").matcher(iframe)
                    // First we find the src
                    if (matcher.find()) {
                        // Snag that src
                        val srcUrl = matcher.group(1) ?: ""
                        if (hasExternalTools(srcUrl)) {
                            // Handle the LTI case
                            val newIframe = externalToolIframe(srcUrl, iframe, context)
                            newHTML = newHTML.replace(iframe, newIframe)
                        } else if (iframe.contains("id=\"cnvs_content\"")) {
                            // Handle the cnvs_content special case for some schools
                            val authenticatedUrl = authenticateLTIUrl(srcUrl)
                            val newIframe = iframe.replace(srcUrl, authenticatedUrl)

                            newHTML = newHTML.replace(iframe, newIframe)
                        }

                        if (iframe.contains("overflow: scroll")) {
                            val newIframe = iframeWithLink(srcUrl, iframe, context)
                            newHTML = newHTML.replace(iframe, newIframe)
                        }

                        if (hasGoogleDocsUrl(srcUrl)) {
                            val newIframe = iframeWithGoogleDocsButton(srcUrl, iframe, context.getString(R.string.openLtiInExternalApp))
                            newHTML = newHTML.replace(iframe, newIframe)
                        }
                    }
                }

                val document = DiscussionHtmlTemplates.getTopicHeader(context)
                val isTablet = context.resources.getBoolean(R.bool.isDeviceTablet)

                newHTML = document.replace("__HEADER_CONTENT__", newHTML)
                newHTML = newHTML.replace("__LTI_BUTTON_WIDTH__", if (isTablet) "320px" else "100%")
                newHTML = newHTML.replace("__LTI_BUTTON_MARGIN__", if (isTablet) "0px" else "auto")

                return CanvasWebView.applyWorkAroundForDoubleSlashesAsUrlSource(newHTML)
            } else {
                return newHTML
            }
        } catch (e: Exception) {
            crashlytics.recordException(e)
            return html
        }
    }

    private fun isWistiaIFrame(iFrame: String): Boolean {
        return iFrame.contains("wistia") &&
                iFrame.contains("embed")
    }

    private suspend fun externalToolIframe(srcUrl: String, iframe: String, context: Context): String {
        // We need to authenticate the src url and replace it within the iframe
        val ltiUrl = URLEncoder.encode(srcUrl, "UTF-8")

        val authenticatedUrl = authenticateLTIUrl(srcUrl)

        // Now we need to replace the iframes src url with the authenticated url
        val newIframe = iframe.replace(srcUrl, authenticatedUrl)

        // With that done, we need to make the LTI launch button
        val button = "</br><p><div class=\"lti_button\" onClick=\"onLtiToolButtonPressed('%s')\">%s</div></p>"
        val htmlButton = String.format(button, ltiUrl, context.resources.getString(R.string.utils_launchExternalTool))

        // Now we add the launch button along with the new iframe with the updated URL
        return newIframe + htmlButton
    }

    private suspend fun authenticateLTIUrl(ltiUrl: String): String {
        val ltiResult = apiAsync<AuthenticatedSession> { oAuthManager.getAuthenticatedSession(ltiUrl, it) }.await()
        return if (ltiResult.isSuccess) {
            return ltiResult.dataOrNull?.sessionUrl ?: ltiUrl
        } else {
            ltiUrl
        }
    }

    private fun iframeWithLink(srcUrl: String, iframe: String, context: Context): String {
        val buttonText = context.getString(R.string.loadFullContent)
        val htmlButton = "</br><p><div class=\"lti_button\" onClick=\"location.href=\'$srcUrl\'\">$buttonText</div></p>"

        return iframe + htmlButton
    }

    private fun iframeWithGoogleDocsButton(srcUrl: String, iframe: String, buttonText: String): String {
        val button = "</br><p><div class=\"lti_button\" onClick=\"googleDocs.onGoogleDocsButtonPressed('%s')\">%s</div></p>"
        val htmlButton = String.format(button, srcUrl, buttonText)
        return iframe + htmlButton
    }

    fun createAuthenticatedLtiUrl(html: String, authenticatedSessionUrl: String?): String {
        if (authenticatedSessionUrl == null) return html
        // Now we need to swap out part of the old url for this new authenticated url
        val matcher = Pattern.compile("src=\"([^;]+)").matcher(html)
        var newHTML: String = html
        if (matcher.find()) {
            // We only want to change the urls that are part of an external tool, not everything (like avatars)
            for (index in 0..matcher.groupCount()) {
                val newUrl = matcher.group(index)
                if (newUrl.contains("external_tools")) {
                    newHTML = html.replace(newUrl, authenticatedSessionUrl)
                }
            }
        }
        return newHTML
    }

    companion object {
        fun hasGoogleDocsUrl(text: String?) = text?.contains("docs.google.com").orDefault()
        fun hasExternalTools(text: String?) = text?.contains("external_tools").orDefault()
    }
}