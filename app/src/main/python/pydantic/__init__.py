class BaseModel:
    def __init__(self, **data):
        for name in self._field_names():
            if name in data:
                value = data.pop(name)
            elif hasattr(self.__class__, name):
                value = getattr(self.__class__, name)
            else:
                value = None
            setattr(self, name, value)

        for name, value in data.items():
            setattr(self, name, value)

    @classmethod
    def _field_names(cls):
        names = []
        for base in reversed(cls.__mro__):
            annotations = getattr(base, "__annotations__", {})
            for name in annotations:
                if not name.startswith("_") and name not in names:
                    names.append(name)
        return names

    def model_dump(self, *args, **kwargs):
        exclude_none = kwargs.get("exclude_none", False)
        data = {}
        for name in self._field_names():
            value = getattr(self, name, None)
            if exclude_none and value is None:
                continue
            data[name] = value
        return data

    def dict(self, *args, **kwargs):
        return self.model_dump(*args, **kwargs)
