import re


class MiroImage:
    def __init__(self, image_info):
        self.collection = image_info['collection'].split("-")[-1]
        self.image_data = image_info['image_data']

    def __repr__(self):
        image_info = {'collection': self.collection, 'image_data': self.image_data}
        return f'{type(self).__name}(image_info={image_info!r})'

    @property
    def miro_id(self):
        return self.image_data['image_no_calc']

    @property
    def shard(self):
        result = re.match(r"(?P<shard>[A-Z]+[0-9]{4})", self.miro_id)
        return f"{result.group('shard')}000"

    @property
    def image_path(self):
        return f"{self.shard}/{self.miro_id}"