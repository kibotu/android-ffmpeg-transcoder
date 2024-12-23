package com.exozet.transcoder.ffmpeg.demo

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlin.apply

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    add(disposable)
}

fun Disposable.addTo(compositeDisposable: CompositeDisposable): Disposable =
    apply { compositeDisposable.add(this) }