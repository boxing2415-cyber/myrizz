@file:OptIn(ExperimentalFoundationApi::class)

package com.myrizz.assistant.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myrizz.assistant.R
import com.myrizz.assistant.ui.components.BrandMark
import com.myrizz.assistant.ui.components.GradientButton
import com.myrizz.assistant.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val finished = rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(550)
        finished.value()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A0F5C), Color(0xFF0B0714)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BrandMark(size = 96.dp, modifier = Modifier.scale(scale.value))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC9BEE0),
            )
        }
    }
}

private data class OnboardingPage(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    OnboardingPage(Icons.Rounded.AutoAwesome, R.string.onb1_title, R.string.onb1_body),
    OnboardingPage(Icons.Rounded.ChatBubble, R.string.onb2_title, R.string.onb2_body),
    OnboardingPage(Icons.Rounded.Lock, R.string.onb3_title, R.string.onb3_body),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone, modifier = Modifier.testTag("onboarding_skip")) {
                Text(stringResource(R.string.action_skip))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Brand.gradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.size(32.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (i == pagerState.currentPage) 22.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        GradientButton(
            text = stringResource(if (last) R.string.onb_get_started else R.string.action_continue),
            onClick = {
                if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_next"),
        )
    }
}
