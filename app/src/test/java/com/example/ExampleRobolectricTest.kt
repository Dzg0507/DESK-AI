package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DeskAI", appName)
  }

  @Test
  fun `test markdown parsing`() {
    val input = """
      # Mission Title
      • Step 1: Initialize
      ```python
      print("Hello AlwaysOnAgent")
      ```
    """.trimIndent()
    val elements = parseMarkdown(input)
    assertTrue(elements.isNotEmpty())
  }
}
