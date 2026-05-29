class RuntimeUnavailableError(RuntimeError):
    pass


class _UnavailableContext:
    def call(self, *args, **kwargs):
        raise RuntimeUnavailableError(
            "ExecJS is not bundled in the Android app because live signing is unused"
        )


def compile(source, cwd=None):
    return _UnavailableContext()
