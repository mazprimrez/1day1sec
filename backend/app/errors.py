class ApiError(Exception):
    """An answer the app should get as `{"error": message}` with this HTTP status.

    The app retries 408, 429 and 5xx by itself and shows "Try again" for anything else,
    so the status decides what happens to the entry on the phone.
    """

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message
