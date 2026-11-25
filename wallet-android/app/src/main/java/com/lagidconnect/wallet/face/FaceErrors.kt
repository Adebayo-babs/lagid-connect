package com.lagidconnect.wallet.face

enum class FaceError { BAD_IMAGE, NO_FACE, FACE_TOO_SMALL, MODEL }

class FaceException(val error: FaceError, message: String) : Exception(message)
